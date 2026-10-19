package Servicio;

import java.awt.print.PrinterJob;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.ServiceUIFactory;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobListener;
import javax.print.event.PrintServiceAttributeListener;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Blindaje del envío al spooler: un driver colgado no debe congelar la UI.
 * Antes del arreglo, {@code job.print()} bloqueaba indefinidamente (evidencia /tmp/redteam/D-before.txt).
 */
public class ImpresionSpoolerTimeoutTest {

    @Test
    public void unSpoolerColgadoNoBloqueaAlLlamador() throws Exception {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(new PrintServiceFalso(true));
        long inicio = System.currentTimeMillis();
        boolean resultado = ServicioImpresionTicket.ejecutarImpresionConTimeout(job, 500);
        long transcurrido = System.currentTimeMillis() - inicio;

        assertFalse("Un spooler colgado debe reportarse como fallo, no como éxito", resultado);
        assertTrue("Debía vencer el plazo acotado, transcurrió " + transcurrido + " ms", transcurrido < 5_000);
    }

    @Test
    public void unSpoolerQueRespondeRetornaExito() throws Exception {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(new PrintServiceFalso(false));
        assertTrue(ServicioImpresionTicket.ejecutarImpresionConTimeout(job, 5_000));
    }

    @Test
    public void elPlazoPorDefectoEsPositivoYElNullSeRechaza() throws Exception {
        assertTrue(ServicioImpresionTicket.TIMEOUT_IMPRESION_MS > 0);
        assertFalse(ServicioImpresionTicket.ejecutarImpresionConTimeout(null, 100));
    }

    /** PrintService mínimo y 2D-capaz (requisito de sun.print.RasterPrinterJob#setPrintService). */
    private static final class PrintServiceFalso implements PrintService {

        private final boolean colgar;

        PrintServiceFalso(boolean colgar) {
            this.colgar = colgar;
        }

        @Override
        public String getName() {
            return "FAKE_SPOOLER_TEST";
        }

        @Override
        public DocFlavor[] getSupportedDocFlavors() {
            return new DocFlavor[]{DocFlavor.SERVICE_FORMATTED.PAGEABLE, DocFlavor.SERVICE_FORMATTED.PRINTABLE};
        }

        @Override
        public boolean isDocFlavorSupported(DocFlavor flavor) {
            return flavor == DocFlavor.SERVICE_FORMATTED.PAGEABLE || flavor == DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        }

        @Override
        public Class<?>[] getSupportedAttributeCategories() {
            return new Class<?>[0];
        }

        @Override
        public boolean isAttributeCategorySupported(Class<? extends Attribute> category) {
            return true;
        }

        @Override
        public Object getDefaultAttributeValue(Class<? extends Attribute> category) {
            return null;
        }

        @Override
        public Object getSupportedAttributeValues(Class<? extends Attribute> category, DocFlavor flavor, AttributeSet attributes) {
            return null;
        }

        @Override
        public boolean isAttributeValueSupported(Attribute attrval, DocFlavor flavor, AttributeSet attributes) {
            return false;
        }

        @Override
        public AttributeSet getUnsupportedAttributes(DocFlavor flavor, AttributeSet attributes) {
            return null;
        }

        @Override
        public ServiceUIFactory getServiceUIFactory() {
            return null;
        }

        @Override
        public PrintServiceAttributeSet getAttributes() {
            return new HashPrintServiceAttributeSet();
        }

        @Override
        public <T extends PrintServiceAttribute> T getAttribute(Class<T> category) {
            return null;
        }

        @Override
        public void addPrintServiceAttributeListener(PrintServiceAttributeListener listener) {
        }

        @Override
        public void removePrintServiceAttributeListener(PrintServiceAttributeListener listener) {
        }

        @Override
        public DocPrintJob createPrintJob() {
            return new DocPrintJob() {
                @Override
                public void print(Doc doc, PrintRequestAttributeSet attributes) {
                    if (!colgar) {
                        return;
                    }
                    try {
                        Thread.sleep(60_000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public PrintService getPrintService() {
                    return PrintServiceFalso.this;
                }

                @Override
                public javax.print.attribute.PrintJobAttributeSet getAttributes() {
                    return new javax.print.attribute.HashPrintJobAttributeSet();
                }

                @Override
                public void addPrintJobListener(PrintJobListener listener) {
                }

                @Override
                public void removePrintJobListener(PrintJobListener listener) {
                }

                @Override
                public void addPrintJobAttributeListener(PrintJobAttributeListener listener, javax.print.attribute.PrintJobAttributeSet attributes) {
                }

                @Override
                public void removePrintJobAttributeListener(PrintJobAttributeListener listener) {
                }
            };
        }
    }
}
