import re

file_path = "src/Servicio/ServicioImpresionTicket.java"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Fix N13 in procesarSalida
content = content.replace("""                    LOGGER.warning(() -> "No se pudo imprimir directamente a " + imp + ". Abriendo visor como alternativa.");
                    return abrirVisor(archivoPdf);""", """                    LOGGER.warning(() -> "No se pudo imprimir directamente a " + imp + ". Abriendo visor como alternativa.");
                    abrirVisor(archivoPdf);
                    return false;""")

content = content.replace("""                    LOGGER.warning("No se pudo despachar a PDF24 Creator. Abriendo visor estándar como alternativa.");
                    return abrirVisor(archivoPdf);""", """                    LOGGER.warning("No se pudo despachar a PDF24 Creator. Abriendo visor estándar como alternativa.");
                    abrirVisor(archivoPdf);
                    return false;""")

# Fix N9 in imprimirTicketPrueba
content = content.replace("""                if (!ok) {
                    return abrirVisor(tempTicket);
                }""", """                if (!ok) {
                    abrirVisor(tempTicket);
                    return false;
                }""")

content = content.replace("""            } else {
                abrirVisor(tempTicket);
                return true;
            }""", """            } else {
                return abrirVisor(tempTicket);
            }""")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Fixed N13 and N9")
