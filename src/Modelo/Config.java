package Modelo;

import java.math.BigDecimal;

public class Config {
    private int id;
    private String ruc;
    private String nombre;
    private String telefono;
    private String direccion;
    private String mensaje;
    private BigDecimal tasaDolar = new BigDecimal("36.5000");
    private BigDecimal ivaPorcentaje = new BigDecimal("16.00");
    private String logoPath;
    private String clienteDefaultNombre = "Consumidor Final";
    private String clienteDefaultDocumento = "V-00000000";
    private int mesesRetencionPedidos = 24;
    private boolean imprimirLogoTicket = true;
    private String impresoraTickets = "DEFAULT";
    private ModoSalidaTicket modoSalidaTickets = ModoSalidaTicket.TERMICA_DIRECTA;

    public Config() {
    }

    public Config(int id, String ruc, String nombre, String telefono, String direccion, String mensaje) {
        this(id, ruc, nombre, telefono, direccion, mensaje, new BigDecimal("36.5000"), new BigDecimal("16.00"));
    }

    public Config(int id, String ruc, String nombre, String telefono, String direccion, String mensaje, BigDecimal tasaDolar) {
        this(id, ruc, nombre, telefono, direccion, mensaje, tasaDolar, new BigDecimal("16.00"));
    }

    public Config(int id, String ruc, String nombre, String telefono, String direccion, String mensaje, BigDecimal tasaDolar, BigDecimal ivaPorcentaje) {
        this(id, ruc, nombre, telefono, direccion, mensaje, tasaDolar, ivaPorcentaje, null);
    }

    public Config(int id, String ruc, String nombre, String telefono, String direccion, String mensaje, BigDecimal tasaDolar, BigDecimal ivaPorcentaje, String logoPath) {
        this.id = id;
        this.ruc = ruc;
        this.nombre = nombre;
        this.telefono = telefono;
        this.direccion = direccion;
        this.mensaje = mensaje;
        setTasaDolar(tasaDolar);
        setIvaPorcentaje(ivaPorcentaje);
        this.logoPath = logoPath;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public BigDecimal getTasaDolar() {
        return tasaDolar;
    }

    public void setTasaDolar(BigDecimal tasaDolar) {
        if (tasaDolar != null && tasaDolar.compareTo(BigDecimal.ZERO) > 0) {
            this.tasaDolar = tasaDolar;
        } else {
            this.tasaDolar = new BigDecimal("36.5000");
        }
    }

    public BigDecimal getTasaCambio() {
        return getTasaDolar();
    }

    public void setTasaCambio(BigDecimal tasa) {
        setTasaDolar(tasa);
    }

    public BigDecimal getIvaPorcentaje() {
        return ivaPorcentaje;
    }

    public void setIvaPorcentaje(BigDecimal ivaPorcentaje) {
        if (ivaPorcentaje == null) {
            this.ivaPorcentaje = new BigDecimal("16.00");
            return;
        }
        if (ivaPorcentaje.compareTo(BigDecimal.ZERO) < 0 || ivaPorcentaje.compareTo(new BigDecimal("100.00")) > 0 || ivaPorcentaje.scale() > 2) {
            throw ErrorAplicacionException.validacion("El porcentaje de IVA debe estar entre 0.00 y 100.00% con hasta dos decimales.");
        }
        this.ivaPorcentaje = ivaPorcentaje.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public String getRif() {
        return getRuc();
    }

    public void setRif(String rif) {
        setRuc(rif);
    }

    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }

    public String getClienteDefaultNombre() {
        return clienteDefaultNombre == null || clienteDefaultNombre.trim().isEmpty()
                ? "Consumidor Final" : clienteDefaultNombre.trim();
    }

    public void setClienteDefaultNombre(String clienteDefaultNombre) {
        if (clienteDefaultNombre == null || clienteDefaultNombre.trim().isEmpty()) {
            this.clienteDefaultNombre = "Consumidor Final";
        } else {
            this.clienteDefaultNombre = clienteDefaultNombre.trim();
        }
    }

    public String getClienteDefaultDocumento() {
        return clienteDefaultDocumento == null || clienteDefaultDocumento.trim().isEmpty()
                ? "V-00000000" : clienteDefaultDocumento.trim();
    }

    public void setClienteDefaultDocumento(String clienteDefaultDocumento) {
        if (clienteDefaultDocumento == null || clienteDefaultDocumento.trim().isEmpty()) {
            this.clienteDefaultDocumento = "V-00000000";
        } else {
            this.clienteDefaultDocumento = clienteDefaultDocumento.trim();
        }
    }

    public String getClientePredeterminadoNombre() {
        return getClienteDefaultNombre();
    }

    public void setClientePredeterminadoNombre(String nombre) {
        setClienteDefaultNombre(nombre);
    }

    public String getClientePredeterminadoDocumento() {
        return getClienteDefaultDocumento();
    }

    public void setClientePredeterminadoDocumento(String doc) {
        setClienteDefaultDocumento(doc);
    }

    public String getClienteDefaultDoc() {
        return getClienteDefaultDocumento();
    }

    public void setClienteDefaultDoc(String doc) {
        setClienteDefaultDocumento(doc);
    }

    public int getMesesRetencionPedidos() {
        return mesesRetencionPedidos;
    }

    public void setMesesRetencionPedidos(int mesesRetencionPedidos) {
        if (mesesRetencionPedidos < 1) {
            throw ErrorAplicacionException.validacion("El período de retención debe ser de al menos 1 mes.");
        }
        this.mesesRetencionPedidos = mesesRetencionPedidos;
    }

    public boolean isImprimirLogoTicket() {
        return imprimirLogoTicket;
    }

    public void setImprimirLogoTicket(boolean imprimirLogoTicket) {
        this.imprimirLogoTicket = imprimirLogoTicket;
    }

    public String getImpresoraTickets() {
        return (impresoraTickets == null || impresoraTickets.isBlank()) ? "DEFAULT" : impresoraTickets.trim();
    }

    public void setImpresoraTickets(String impresoraTickets) {
        this.impresoraTickets = (impresoraTickets == null || impresoraTickets.isBlank()) ? "DEFAULT" : impresoraTickets.trim();
    }

    public ModoSalidaTicket getModoSalidaTickets() {
        return modoSalidaTickets != null ? modoSalidaTickets : ModoSalidaTicket.TERMICA_DIRECTA;
    }

    public void setModoSalidaTickets(ModoSalidaTicket modoSalidaTickets) {
        this.modoSalidaTickets = modoSalidaTickets != null ? modoSalidaTickets : ModoSalidaTicket.TERMICA_DIRECTA;
    }
}
