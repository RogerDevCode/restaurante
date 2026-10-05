
package Modelo;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.filechooser.FileSystemView;

public class PedidosDao implements PedidosRepositorio {
    private final Conexion cn = new Conexion();
    
    public int verificarStado(int mesa, int id_sala){
        if (mesa <= 0 || id_sala <= 0) {
            throw ErrorAplicacionException.validacion("La sala y el número de mesa deben ser válidos.");
        }
        int id_pedido = 0;
        String sql = "SELECT id FROM pedidos WHERE num_mesa=? AND id_sala=? AND estado = ?";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, mesa);
            sentencia.setInt(2, id_sala);
            sentencia.setString(3, "PENDIENTE");
            try (ResultSet resultados = sentencia.executeQuery()) {
                if (resultados.next()) {
                    id_pedido = resultados.getInt("id");
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo consultar el estado de la mesa.", ex);
        }
        return id_pedido;
    }
    
    @Override
    public int registrarPedidoCompleto(Pedidos pedido, List<DetallePedido> detalles) {
        if (pedido == null) {
            throw ErrorAplicacionException.validacion("El pedido es obligatorio.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw ErrorAplicacionException.validacion("El pedido debe incluir al menos un detalle.");
        }
        for (DetallePedido detalle : detalles) {
            if (detalle == null) {
                throw ErrorAplicacionException.validacion("El pedido contiene un detalle vacío.");
            }
        }

        String sqlPedido = "INSERT INTO pedidos (id_sala, num_mesa, total, usuario) VALUES (?,?,?,?)";
        String sqlDetalle = "INSERT INTO detalle_pedidos (nombre, precio, cantidad, comentario, id_pedido) VALUES (?,?,?,?,?)";

        try (Connection conexion = cn.getConnection()) {
            try {
                conexion.setAutoCommit(false);
                int idPedido;
                try (PreparedStatement sentenciaPedido = conexion.prepareStatement(sqlPedido, Statement.RETURN_GENERATED_KEYS)) {
                    sentenciaPedido.setInt(1, pedido.getId_sala());
                    sentenciaPedido.setInt(2, pedido.getNum_mesa());
                    sentenciaPedido.setDouble(3, pedido.getTotal());
                    sentenciaPedido.setString(4, pedido.getUsuario());
                    if (sentenciaPedido.executeUpdate() != 1) {
                        throw new SQLException("No se pudo insertar el encabezado del pedido.");
                    }
                    try (ResultSet claves = sentenciaPedido.getGeneratedKeys()) {
                        if (!claves.next()) {
                            throw new SQLException("La base de datos no devolvió el ID del pedido.");
                        }
                        idPedido = claves.getInt(1);
                        if (idPedido <= 0) {
                            throw new SQLException("La base de datos devolvió un ID de pedido no válido.");
                        }
                    }
                }

                try (PreparedStatement sentenciaDetalle = conexion.prepareStatement(sqlDetalle)) {
                    for (DetallePedido detalle : detalles) {
                        sentenciaDetalle.setString(1, detalle.getNombre());
                        sentenciaDetalle.setDouble(2, detalle.getPrecio());
                        sentenciaDetalle.setInt(3, detalle.getCantidad());
                        sentenciaDetalle.setString(4, detalle.getComentario());
                        sentenciaDetalle.setInt(5, idPedido);
                        if (sentenciaDetalle.executeUpdate() != 1) {
                            throw new SQLException("No se pudo insertar uno de los detalles del pedido.");
                        }
                    }
                }

                conexion.commit();
                return idPedido;
            } catch (SQLException | RuntimeException error) {
                try {
                    conexion.rollback();
                } catch (SQLException errorRollback) {
                    error.addSuppressed(errorRollback);
                }
                if (error instanceof SQLException) {
                    SQLException errorSql = (SQLException) error;
                    String mensaje = errorSql.getMessage();
                    if (errorSql.getErrorCode() == 1062
                            && mensaje != null
                            && mensaje.contains("uq_pedidos_mesa_pendiente")) {
                        throw new PedidoPendienteExistenteException(pedido.getNum_mesa(), errorSql);
                    }
                    throw new DataAccessException("No se pudo guardar el pedido completo.", errorSql);
                }
                throw new ErrorAplicacionException("Falló el guardado completo del pedido.", error);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo abrir la conexión para guardar el pedido.", ex);
        }
    }
    
    public List verPedidoDetalle(int id_pedido){
       if (id_pedido <= 0) {
           throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
       }
       List<DetallePedido> Lista = new ArrayList();
       String sql = "SELECT d.* FROM pedidos p INNER JOIN detalle_pedidos d ON p.id = d.id_pedido WHERE p.id = ?";
       try (Connection conexion = cn.getConnection();
               PreparedStatement sentencia = conexion.prepareStatement(sql)) {
           sentencia.setInt(1, id_pedido);
           try (ResultSet resultados = sentencia.executeQuery()) {
           while (resultados.next()) {
               DetallePedido det = new DetallePedido();
               det.setId(resultados.getInt("id"));
               det.setNombre(resultados.getString("nombre"));
               det.setPrecio(resultados.getDouble("precio"));
               det.setCantidad(resultados.getInt("cantidad"));
               det.setComentario(resultados.getString("comentario"));
               Lista.add(det);
           }
           }
       } catch (SQLException ex) {
           throw new DataAccessException("No se pudieron consultar los detalles del pedido.", ex);
       }
       if (Lista.isEmpty()) {
           throw new ErrorAplicacionException(
                   "El pedido " + id_pedido + " no tiene detalles asociados.",
                   new IllegalStateException("Un pedido debe contener al menos un detalle."));
       }
       return Lista;
   }
    
    public Pedidos verPedido(int id_pedido){
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
        }
        Pedidos ped = null;
       String sql = "SELECT p.*, s.nombre FROM pedidos p INNER JOIN salas s ON p.id_sala = s.id WHERE p.id = ?";
       try (Connection conexion = cn.getConnection();
               PreparedStatement sentencia = conexion.prepareStatement(sql)) {
           sentencia.setInt(1, id_pedido);
           try (ResultSet resultados = sentencia.executeQuery()) {
            if (resultados.next()) {
               ped = new Pedidos();
               
               ped.setId(resultados.getInt("id"));
               ped.setFecha(resultados.getString("fecha"));
               ped.setSala(resultados.getString("nombre"));
               ped.setNum_mesa(resultados.getInt("num_mesa"));
               ped.setTotal(resultados.getDouble("total"));
            }
           }
       } catch (SQLException ex) {
           throw new DataAccessException("No se pudo consultar el pedido.", ex);
       }
       if (ped == null) {
           throw new ErrorAplicacionException(
                   "No existe el pedido " + id_pedido + ".",
                   new IllegalStateException("La consulta no encontró el pedido solicitado."));
       }
       return ped;
   }
    
    public List finalizarPedido(int id_pedido){
       if (id_pedido <= 0) {
           throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido.");
       }
       List<DetallePedido> Lista = new ArrayList();
       String sql = "SELECT d.* FROM pedidos p INNER JOIN detalle_pedidos d ON p.id = d.id_pedido WHERE p.id = ?";
       try (Connection conexion = cn.getConnection();
               PreparedStatement sentencia = conexion.prepareStatement(sql)) {
           sentencia.setInt(1, id_pedido);
           try (ResultSet resultados = sentencia.executeQuery()) {
           while (resultados.next()) {
               DetallePedido det = new DetallePedido();
               det.setId(resultados.getInt("id"));
               det.setNombre(resultados.getString("nombre"));
               det.setPrecio(resultados.getDouble("precio"));
               det.setCantidad(resultados.getInt("cantidad"));
               det.setComentario(resultados.getString("comentario"));
               Lista.add(det);
           }
           }
       } catch (SQLException ex) {
           throw new DataAccessException("No se pudieron consultar los detalles del pedido.", ex);
       }
       if (Lista.isEmpty()) {
           throw new ErrorAplicacionException(
                   "El pedido " + id_pedido + " no tiene detalles asociados.",
                   new IllegalStateException("Un pedido debe contener al menos un detalle."));
       }
       return Lista;
   }
    
    public void pdfPedido(int id_pedido) {
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para generar el PDF.");
        }
        String fechaPedido = "";
        String usuario = "";
        String total = "";
        String sala = "";
        String numMesa = "";
        String mensaje = "";
        String datosEmpresa = "";
        List<DetallePedido> detalles = new ArrayList<>();
        boolean existeConfiguracion = false;

        String informacion = "SELECT p.*, s.nombre FROM pedidos p INNER JOIN salas s ON p.id_sala = s.id WHERE p.id = ?";
        String consultaConfig = "SELECT * FROM config";
        String consultaDetalles = "SELECT d.* FROM detalle_pedidos d WHERE d.id_pedido = ?";
        try (Connection conexion = cn.getConnection()) {
            try (PreparedStatement sentencia = conexion.prepareStatement(informacion)) {
                sentencia.setInt(1, id_pedido);
                try (ResultSet resultados = sentencia.executeQuery()) {
                    if (!resultados.next()) {
                        throw new SQLException("No existe el pedido " + id_pedido + ".");
                    }
                    numMesa = resultados.getString("num_mesa");
                    sala = resultados.getString("nombre");
                    fechaPedido = resultados.getString("fecha");
                    usuario = resultados.getString("usuario");
                    total = resultados.getString("total");
                }
            }
            try (PreparedStatement sentencia = conexion.prepareStatement(consultaConfig);
                ResultSet resultados = sentencia.executeQuery()) {
                if (resultados.next()) {
                    existeConfiguracion = true;
                    mensaje = resultados.getString("mensaje");
                    datosEmpresa = "Ruc:    " + resultados.getString("ruc")
                            + "\nNombre: " + resultados.getString("nombre")
                            + "\nTeléfono: " + resultados.getString("telefono")
                            + "\nDirección: " + resultados.getString("direccion");
                }
            }
            try (PreparedStatement sentencia = conexion.prepareStatement(consultaDetalles)) {
                sentencia.setInt(1, id_pedido);
                try (ResultSet resultados = sentencia.executeQuery()) {
                    while (resultados.next()) {
                        DetallePedido detalle = new DetallePedido();
                        detalle.setNombre(resultados.getString("nombre"));
                        detalle.setPrecio(resultados.getDouble("precio"));
                        detalle.setCantidad(resultados.getInt("cantidad"));
                        detalles.add(detalle);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudieron consultar los datos del pedido para el PDF.", ex);
        }
        if (!existeConfiguracion) {
            throw new ErrorAplicacionException(
                    "No existe la configuración de la empresa para generar el PDF.",
                    new IllegalStateException("La tabla config no contiene un registro."));
        }
        if (detalles.isEmpty()) {
            throw new ErrorAplicacionException(
                    "El pedido " + id_pedido + " no tiene detalles para generar el PDF.",
                    new IllegalStateException("Un pedido debe contener al menos un detalle."));
        }

        String url = FileSystemView.getFileSystemView().getDefaultDirectory().getPath();
        File salida = new File(url + File.separator + "pedido.pdf");
        Document documento = new Document();
        try (FileOutputStream archivo = new FileOutputStream(salida)) {
            PdfWriter.getInstance(documento, archivo);
            documento.open();
            Image imagen = Image.getInstance(getClass().getResource("/Img/logo.png"));

            PdfPTable encabezado = new PdfPTable(4);
            encabezado.setWidthPercentage(100);
            encabezado.getDefaultCell().setBorder(0);
            encabezado.setWidths(new float[]{20f, 20f, 60f, 60f});
            encabezado.setHorizontalAlignment(Element.ALIGN_LEFT);
            encabezado.addCell(imagen);
            encabezado.addCell("");
            encabezado.addCell(datosEmpresa);

            Paragraph info = new Paragraph("Atendido: " + usuario
                    + "\nN° Pedido: " + id_pedido
                    + "\nFecha: " + fechaPedido
                    + "\nSala: " + sala
                    + "\nN° Mesa: " + numMesa);
            encabezado.addCell(info);
            documento.add(encabezado);
            documento.add(Chunk.NEWLINE);

            Font negrita = new Font(Font.FontFamily.TIMES_ROMAN, 12, Font.BOLD, BaseColor.BLUE);
            PdfPTable tabla = new PdfPTable(4);
            tabla.setWidthPercentage(100);
            tabla.getDefaultCell().setBorder(0);
            tabla.setWidths(new float[]{10f, 50f, 15f, 15f});
            tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
            PdfPCell[] cabeceras = {
                new PdfPCell(new Phrase("Cant.", negrita)),
                new PdfPCell(new Phrase("Plato.", negrita)),
                new PdfPCell(new Phrase("P. unt.", negrita)),
                new PdfPCell(new Phrase("P. Total", negrita))
            };
            for (PdfPCell celda : cabeceras) {
                celda.setBorder(Rectangle.NO_BORDER);
                celda.setBackgroundColor(BaseColor.LIGHT_GRAY);
                tabla.addCell(celda);
            }
            for (DetallePedido detalle : detalles) {
                double subtotal = detalle.getCantidad() * detalle.getPrecio();
                tabla.addCell(String.valueOf(detalle.getCantidad()));
                tabla.addCell(detalle.getNombre());
                tabla.addCell(String.valueOf(detalle.getPrecio()));
                tabla.addCell(String.valueOf(subtotal));
            }
            documento.add(tabla);

            Paragraph totalPedido = new Paragraph("Total S/: " + total);
            totalPedido.setAlignment(Element.ALIGN_RIGHT);
            documento.add(Chunk.NEWLINE);
            documento.add(totalPedido);
            Paragraph firma = new Paragraph("Cancelación \n\n------------------------------------\nFirma\n");
            firma.setAlignment(Element.ALIGN_CENTER);
            documento.add(Chunk.NEWLINE);
            documento.add(firma);
            Paragraph agradecimiento = new Paragraph(mensaje);
            agradecimiento.setAlignment(Element.ALIGN_CENTER);
            documento.add(Chunk.NEWLINE);
            documento.add(agradecimiento);
            documento.close();
        } catch (DocumentException | IOException ex) {
            throw new ErrorAplicacionException("No se pudo generar el PDF del pedido.", ex);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
        try {
            Desktop.getDesktop().open(salida);
        } catch (IOException ex) {
            throw new ErrorAplicacionException("El PDF se generó, pero no se pudo abrir automáticamente.", ex);
        }
    }
    
    public boolean actualizarEstado (int id_pedido){
        if (id_pedido <= 0) {
            throw ErrorAplicacionException.validacion("El identificador del pedido debe ser válido para finalizarlo.");
        }
        String sql = "UPDATE pedidos SET estado = ? WHERE id = ?";
        try (Connection conexion = cn.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, "FINALIZADO");
            sentencia.setInt(2, id_pedido);
            return ErrorAplicacionException.resultadoUnaFila(
                    sentencia.executeUpdate(), "finalizar pedido " + id_pedido);
        } catch (SQLException ex) {
            throw new DataAccessException("No se pudo finalizar el pedido.", ex);
        }
    }
    
    public List listarPedidos(){
       List<Pedidos> Lista = new ArrayList();
       String sql = "SELECT p.*, s.nombre FROM pedidos p INNER JOIN salas s ON p.id_sala = s.id ORDER BY p.fecha DESC";
       try (Connection conexion = cn.getConnection();
               PreparedStatement sentencia = conexion.prepareStatement(sql);
               ResultSet resultados = sentencia.executeQuery()) {
           while (resultados.next()) {
               Pedidos ped = new Pedidos();
               ped.setId(resultados.getInt("id"));
               ped.setSala(resultados.getString("nombre"));
               ped.setNum_mesa(resultados.getInt("num_mesa"));
               ped.setFecha(resultados.getString("fecha"));
               ped.setTotal(resultados.getDouble("total"));
               ped.setUsuario(resultados.getString("usuario"));
               ped.setEstado(resultados.getString("estado"));
               Lista.add(ped);
           }
       } catch (SQLException ex) {
           throw new DataAccessException("No se pudieron listar los pedidos.", ex);
       }
       return Lista;
   }
    
}
