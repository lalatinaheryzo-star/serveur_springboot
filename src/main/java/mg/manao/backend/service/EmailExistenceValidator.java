package mg.manao.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Vérifie qu'une adresse e-mail est réellement susceptible d'exister, avant
 * de créer un compte (§ demande de Malala : "une adresse qui n'existe pas
 * réellement ne doit pas être acceptée").
 *
 * Deux niveaux de contrôle, combinés :
 *  1) Ici : le DOMAINE de l'adresse doit pouvoir recevoir du courrier
 *     (enregistrement DNS MX, ou à défaut un enregistrement A — RFC 5321 §5).
 *     Rapide, gratuit, ne dépend d'aucun service tiers payant. Élimine
 *     immédiatement les domaines inventés ou mal orthographiés
 *     ("...@gnail.con", "...@test123.xyz" sans MX, etc.).
 *  2) EmailVerificationService : la BOÎTE elle-même (partie locale) doit
 *     être réellement accessible par la personne qui s'inscrit — vérifié en
 *     lui envoyant un code de confirmation qu'elle doit saisir. C'est cette
 *     étape qui prouve qu'une adresse syntaxiquement valide sur un domaine
 *     existant est bien "réellement existante et accessible", sans dépendre
 *     d'un service externe payant (SMTP handshake direct peu fiable : de
 *     nombreux hébergeurs bloquent le port 25 sortant, et Gmail notamment
 *     n'accepte pas les vérifications RCPT TO à l'aveugle).
 *
 * MX et A sont interrogés EN PARALLÈLE (et non plus l'un après l'autre) avec
 * un délai d'attente court et une seule tentative chacun : la réponse à
 * l'inscription doit arriver vite. Avant, le pire cas (domaine sans MX,
 * DNS lent) enchaînait 2 lookups x 2 tentatives x 3s = jusqu'à 12 secondes
 * d'attente ; désormais le pire cas est d'environ 1,5 seconde.
 */
@Component
public class EmailExistenceValidator {

    private static final Logger log = LoggerFactory.getLogger(EmailExistenceValidator.class);

    /** @return true si le domaine de l'adresse a un enregistrement MX ou A exploitable. */
    public boolean domaineAcceptantDuCourrier(String email) {
        String domaine = extraireDomaine(email);
        if (domaine == null) return false;

        CompletableFuture<Attribute> mx = CompletableFuture.supplyAsync(() -> lookup(domaine, "MX"));
        CompletableFuture<Attribute> a  = CompletableFuture.supplyAsync(() -> lookup(domaine, "A"));

        return !isEmpty(mx.join()) || !isEmpty(a.join());
    }

    private String extraireDomaine(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) return null;
        return email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    private Attribute lookup(String domaine, String type) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        // Ne bloque jamais longtemps une requête d'inscription : si le DNS ne
        // répond pas rapidement, on ne rejette pas l'utilisateur pour autant
        // (voir appelant : en cas de doute, on laisse passer et c'est l'étape
        // 2 — code de vérification par e-mail — qui tranche réellement).
        // Une seule tentative (pas de retry) : un DNS qui ne répond pas en
        // 1,5s ne répondra probablement pas mieux à la 2e tentative, autant
        // basculer immédiatement sur le repli (A, puis l'inscription elle-même).
        env.put("com.sun.jndi.dns.timeout.initial", "1500");
        env.put("com.sun.jndi.dns.timeout.retries", "0");
        try {
            Attributes attrs = new InitialDirContext(env).getAttributes(domaine, new String[]{type});
            return attrs.get(type);
        } catch (NamingException e) {
            log.debug("Pas d'enregistrement {} pour le domaine {} ({})", type, domaine, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Vérification DNS impossible pour le domaine {} : {}", domaine, e.getMessage());
            return null;
        }
    }

    private boolean isEmpty(Attribute attr) {
        return attr == null || attr.size() == 0;
    }
}
