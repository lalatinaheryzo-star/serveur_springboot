package mg.manao.backend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import mg.manao.backend.dto.RecuDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * Génère le PDF du reçu. Le contenu reprend exactement les mêmes sections que
 * l'affichage écran (voir pages/user/RecuVoyageur.jsx côté frontend) : bandeau
 * coopérative + statut, informations voyageur, détails du voyage, réservation
 * & paiement, QR Code de vérification, note de bas de page — pour que le PDF
 * téléchargé corresponde fidèlement à ce que le voyageur voit à l'écran.
 */
@Service
public class PdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] generateRecuPdf(RecuDTO r) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDFont bold = PDType1Font.HELVETICA_BOLD;
            PDFont regular = PDType1Font.HELVETICA;

            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            float width = page.getMediaBox().getWidth() - 2 * margin;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // ── En-tête : coopérative + statut du reçu (= pastille écran) ──
                y = writeLine(cs, bold, 17, margin, y, "RÉSERVATION EN LIGNE");
                y -= 4;
                y = writeLine(cs, regular, 9, margin, y, "Reçu de réservation officiel  ·  N° " + safe(r.getNumeroRecu()));
                String statutPill = "Embarquée".equals(r.getStatut()) ? "EMBARQUÉE" : "CONFIRMÉE";
                y = writeLine(cs, bold, 9, margin, y, "Statut du reçu : " + statutPill);
                y -= 8;

                y = writeLine(cs, bold, 12, margin, y, safe(r.getCooperativeNom(), "Coopérative de transport"));
                if (r.getCooperativeTelephone() != null) {
                    y = writeLine(cs, regular, 9, margin, y, "Tél : " + r.getCooperativeTelephone());
                }
                y -= 8;
                y = hr(cs, margin, y, width);
                y -= 18;

                // ── Informations du voyageur ──
                y = writeLine(cs, bold, 11, margin, y, "Informations du voyageur");
                y = writeLine(cs, regular, 10, margin, y, "Nom complet : " + safe(r.getVoyageurNom()));
                y = writeLine(cs, regular, 10, margin, y, "Téléphone : " + safe(r.getVoyageurTelephone()));
                y -= 8;

                // ── Détails du voyage ──
                y = writeLine(cs, bold, 11, margin, y, "Détails du voyage");
                y = writeLine(cs, regular, 10, margin, y, "Trajet : " + safe(r.getVilleDepart()) + "  ->  " + safe(r.getVilleArrivee()));
                y = writeLine(cs, regular, 10, margin, y, "Date du voyage : " + safe(r.getDateDepart()));
                y = writeLine(cs, regular, 10, margin, y, "Heure de départ : " + safe(shortHeure(r.getHeureDepart())));
                y = writeLine(cs, regular, 10, margin, y, "Numéro de place : " + (r.getNumeroPlace() != null ? "Place " + r.getNumeroPlace() : "-"));
                y -= 8;

                // ── Réservation & paiement ──
                y = writeLine(cs, bold, 11, margin, y, "Réservation & paiement");
                y = writeLine(cs, regular, 10, margin, y,
                        "Date de réservation : " + (r.getDateReservation() != null ? r.getDateReservation().format(DATE_FMT) : "-"));
                y = writeLine(cs, regular, 10, margin, y, "Statut : " + safe(r.getStatutReservationLabel()));
                y = writeLine(cs, regular, 10, margin, y, "Mode de paiement : " + safe(r.getModePaiement()));
                y = writeLine(cs, regular, 10, margin, y,
                        "Montant payé : " + (r.getMontant() != null ? r.getMontant() + " Ar" : "-"));
                y -= 10;
                y = hr(cs, margin, y, width);
                y -= 20;

                // ── QR Code de vérification (même contenu que l'écran : verify_url) ──
                float qrSize = 130;
                BufferedImage qrImage = safeGenerateQr(r.getVerifyUrl(), 180);
                if (qrImage != null) {
                    PDImageXObject qrObj = LosslessFactory.createFromImage(doc, qrImage);
                    cs.drawImage(qrObj, margin, y - qrSize, qrSize, qrSize);
                }
                float textX = margin + qrSize + 20;
                float textY = y - 14;
                textY = writeLine(cs, bold, 11, textX, textY, "Vérification à l'embarquement");
                textY = writeWrapped(cs, regular, 9, textX, textY, width - qrSize - 20,
                        "Présentez ce QR Code à l'agent de la gare le jour du départ. "
                                + "Il sera scanné pour confirmer votre réservation et valider l'embarquement. "
                                + "Ce code ne peut être utilisé qu'une seule fois.");
                y -= (qrSize + 20);

                // ── Pied de page ──
                y = hr(cs, margin, y, width);
                y -= 16;
                y = writeLine(cs, regular, 8, margin, y,
                        "Reçu généré automatiquement le "
                                + (r.getDateGeneration() != null ? r.getDateGeneration().format(DATE_FMT) : "-")
                                + ". Présentez-le (imprimé ou sur téléphone) le jour du départ.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la génération du PDF du reçu.", e);
        }
    }

    /** Génère l'image du QR Code ; retourne null si le contenu est absent ou en cas d'échec (le PDF reste généré sans QR plutôt que d'échouer entièrement). */
    private BufferedImage safeGenerateQr(String content, int size) {
        if (content == null || content.isBlank()) return null;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < size; x++) {
                for (int py = 0; py < size; py++) {
                    image.setRGB(x, py, matrix.get(x, py) ? 0x0B1530 : 0xFFFFFF);
                }
            }
            return image;
        } catch (WriterException e) {
            return null;
        }
    }

    private String shortHeure(String heure) {
        return (heure != null && heure.length() >= 5) ? heure.substring(0, 5) : heure;
    }

    private float writeLine(PDPageContentStream cs, PDFont font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitizeForPdf(text));
        cs.endText();
        return y - (size + 6);
    }

    /** Retour à la ligne manuel (PDFBox ne gère pas le wrap automatique) selon la largeur disponible en points. */
    private float writeWrapped(PDPageContentStream cs, PDFont font, float size, float x, float y, float maxWidth, String text) throws IOException {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float curY = y;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            float w = font.getStringWidth(sanitizeForPdf(candidate)) / 1000 * size;
            if (w > maxWidth && !line.isEmpty()) {
                curY = writeLine(cs, font, size, x, curY, line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            curY = writeLine(cs, font, size, x, curY, line.toString());
        }
        return curY;
    }

    /**
     * La police standard utilisée ici (Helvetica) ne supporte que l'encodage
     * WinAnsi (ASCII + accents latins de base). Tout caractère hors de cette
     * plage présent dans une donnée dynamique (nom de coopérative, de
     * voyageur, de ville saisis en base) fait planter PDFBox avec une
     * IllegalArgumentException non interceptée -> 500 côté téléchargement.
     * On neutralise donc ici, une fois pour toutes, n'importe quel caractère
     * problématique avant de l'écrire dans le PDF.
     */
    private static String sanitizeForPdf(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\u2192' -> sb.append("->");                 // →
                case '\u2013', '\u2014' -> sb.append('-');         // – —
                case '\u2018', '\u2019' -> sb.append('\'');        // ‘ ’
                case '\u201C', '\u201D' -> sb.append('"');         // " "
                case '\u2026' -> sb.append("...");                 // …
                default -> {
                    if (c <= 0xFF) {
                        sb.append(c); // ASCII + Latin-1 (accents français) : supporté par WinAnsiEncoding
                    } else {
                        sb.append('?'); // caractère non supporté par la police standard
                    }
                }
            }
        }
        return sb.toString();
    }

    private float hr(PDPageContentStream cs, float x, float y, float width) throws IOException {
        cs.moveTo(x, y);
        cs.lineTo(x + width, y);
        cs.stroke();
        return y;
    }

    private String safe(String s) { return s == null ? "-" : s; }
    private String safe(String s, String fallback) { return (s == null || s.isBlank()) ? fallback : s; }
}