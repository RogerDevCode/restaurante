package infraestructura.impresion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Protocolo de sondeo bidireccional en tiempo real para impresoras ESC/POS mediante comandos DLE EOT.
 * Permite verificar el estado físico del cabezal, la tapa y los sensores de papel antes de enviar
 * trabajos de impresión a través de sockets TCP crudos (puerto 9100).
 */
public final class ProtocoloEscPosDleEot {

    private static final Logger LOGGER = Logger.getLogger(ProtocoloEscPosDleEot.class.getName());

    public static final byte DLE = 0x10;
    public static final byte EOT = 0x04;

    private ProtocoloEscPosDleEot() {
    }

    public record EstadoImpresora(
            boolean online,
            boolean tapaAbierta,
            boolean sinPapel,
            boolean pocoPapel,
            boolean errorGeneral,
            String descripcion
    ) {
        public boolean isListaParaImprimir() {
            return online && !tapaAbierta && !sinPapel && !errorGeneral;
        }

        public static EstadoImpresora desconocida(String error) {
            return new EstadoImpresora(false, false, false, false, true, "Inaccesible: " + error);
        }
    }

    /**
     * Consulta el estado en tiempo real de la impresora conectada al socket mediante comandos DLE EOT 1, 2 y 4.
     *
     * @param socket    Socket TCP abierto con la impresora.
     * @param timeoutMs Tiempo de espera máximo por respuesta en milisegundos.
     * @return Estado detallado de la impresora térmica.
     */
    public static EstadoImpresora sondear(Socket socket, int timeoutMs) {
        if (socket == null || !socket.isConnected() || socket.isClosed()) {
            return EstadoImpresora.desconocida("Socket no conectado");
        }

        try {
            int originalTimeout = socket.getSoTimeout();
            socket.setSoTimeout(timeoutMs);

            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            // 1. Limpiar buffer previo si hubiere datos residuales
            while (is.available() > 0) {
                is.read();
            }

            // 2. DLE EOT 1: Estado de conexión de impresora
            os.write(new byte[]{DLE, EOT, 1});
            os.flush();
            int b1 = leerByte(is);
            if (b1 < 0) {
                return EstadoImpresora.desconocida("Sin respuesta a DLE EOT 1");
            }
            boolean online = (b1 & 0x08) == 0; // Bit 3: 0 = Online, 1 = Offline

            // 3. DLE EOT 2: Causa de estado offline (Tapa / Errores)
            os.write(new byte[]{DLE, EOT, 2});
            os.flush();
            int b2 = leerByte(is);
            if (b2 < 0) {
                return EstadoImpresora.desconocida("Sin respuesta a DLE EOT 2");
            }
            boolean tapaAbierta = (b2 & 0x04) != 0; // Bit 2: 1 = Tapa abierta
            boolean errorGeneral = (b2 & 0x40) != 0; // Bit 6: 1 = Error

            // 4. DLE EOT 4: Estado de sensores de rollo de papel
            os.write(new byte[]{DLE, EOT, 4});
            os.flush();
            int b4 = leerByte(is);
            if (b4 < 0) {
                return EstadoImpresora.desconocida("Sin respuesta a DLE EOT 4");
            }
            boolean sinPapel = (b4 & 0x60) == 0x60; // Bits 5 y 6: 11 = Papel agotado
            boolean pocoPapel = (b4 & 0x0C) == 0x0C; // Bits 2 y 3: 11 = Poco papel

            socket.setSoTimeout(originalTimeout);

            StringBuilder desc = new StringBuilder();
            if (!online) desc.append("[OFFLINE] ");
            if (tapaAbierta) desc.append("[TAPA ABIERTA] ");
            if (sinPapel) desc.append("[SIN PAPEL] ");
            if (pocoPapel && !sinPapel) desc.append("[POCO PAPEL] ");
            if (errorGeneral) desc.append("[ERROR MECÁNICO] ");

            if (desc.isEmpty()) {
                desc.append("LISTA Y OPERATIVA");
            }

            return new EstadoImpresora(online, tapaAbierta, sinPapel, pocoPapel, errorGeneral, desc.toString().trim());

        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Excepción durante sondeo DLE EOT de impresora: " + ex.getMessage());
            return EstadoImpresora.desconocida(ex.getMessage());
        }
    }

    private static int leerByte(InputStream is) throws IOException {
        long inicio = System.currentTimeMillis();
        while (System.currentTimeMillis() - inicio < 1500) {
            int b = is.read();
            if (b != -1) return b;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return -1;
    }
}
