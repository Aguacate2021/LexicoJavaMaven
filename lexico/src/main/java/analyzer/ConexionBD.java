package analyzer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;

public class ConexionBD {

    private static final String SERVIDOR =
            "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC";

    private static final String BD =
            "Ambito_23130243";

    private static final String URL =
        "jdbc:mysql://localhost:3306/" + BD +
        "?useSSL=false" +
        "&serverTimezone=UTC" +
        "&allowPublicKeyRetrieval=true";

    private static final String USUARIO = "root";
    private static final String PASSWORD = "root";

    /**
     * Crea la base de datos y la tabla si todavía no existen.
     */
    public static void inicializar() {

        try (Connection conexion = DriverManager.getConnection(
                SERVIDOR, USUARIO, PASSWORD);
             Statement st = conexion.createStatement()) {

            // Crear base de datos
            st.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS " + BD +
                    " CHARACTER SET utf8mb4 " +
                    " COLLATE utf8mb4_unicode_ci"
            );

            System.out.println("Base de datos verificada.");

            // Crear tabla
            try (Connection conexionBD =
                         DriverManager.getConnection(
                                 URL, USUARIO, PASSWORD);
                 Statement stBD = conexionBD.createStatement()) {

                String sql = """
                    CREATE TABLE IF NOT EXISTS tabla_simbolos (
                        id VARCHAR(100) NOT NULL,
                        tipo VARCHAR(50),
                        clase VARCHAR(20),
                        amb INT NOT NULL,
                        tarr VARCHAR(50),
                        dimarr VARCHAR(20),
                        nopar INT,
                        tpar VARCHAR(100),

                        PRIMARY KEY (id, amb)
                    )
                    """;

                stBD.executeUpdate(sql);

                System.out.println("Tabla de símbolos verificada.");
            }

        } catch (SQLException e) {

            System.err.println(
                    "Error al inicializar la base de datos:"
            );

            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Conexión compartida
    //
    // Antes cada consulta abría (y cerraba) una conexión TCP nueva: una sola
    // compilación de la prueba grande abría ~24,000 conexiones en ~25 s. Cada
    // socket cerrado queda en TIME_WAIT varios minutos; en Windows (16,384
    // puertos dinámicos) los puertos se agotaban a mitad del análisis, las
    // consultas fallaban y idExiste respondía "no existe": cientos de errores
    // de ámbito falsos. Ahora se reutiliza UNA conexión física durante todo el
    // análisis. Los try-with-resources existentes siguen funcionando: el
    // close() de la conexión que entrega conectar() no cierra la física.
    // ─────────────────────────────────────────────────────────────────────

    /** Fallo de comunicación con la base de datos (no es un "identificador inexistente"). */
    public static class FalloBaseDatos extends RuntimeException {
        public FalloBaseDatos(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private static Connection fisica;     // conexión real, reutilizada
    private static Connection compartida; // envoltura cuyo close() no cierra la física
    private static long ultimoUso;
    private static final long REVALIDAR_MS = 1_000; // tras 1 s sin uso (p. ej. entre compilaciones) se comprueba que siga viva

    /**
     * Obtiene la conexión a la base de datos (siempre la misma mientras siga viva).
     * Los llamadores pueden seguir usando try-with-resources: cerrar la conexión
     * devuelta no cierra la conexión física.
     */
    public static synchronized Connection conectar() throws SQLException {
        long ahora = System.currentTimeMillis();
        boolean viva = fisica != null && !fisica.isClosed()
                && (ahora - ultimoUso < REVALIDAR_MS || fisica.isValid(2));
        if (!viva) {
            cerrarFisica();
            fisica = DriverManager.getConnection(URL, USUARIO, PASSWORD);
            compartida = envolver(fisica);
        }
        ultimoUso = ahora;
        return compartida;
    }

    private static Connection envolver(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, metodo, args) -> {
                    if ("close".equals(metodo.getName())) {
                        return null; // la conexión física se conserva para la siguiente consulta
                    }
                    try {
                        return metodo.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** Descarta la conexión física (la siguiente llamada a conectar() abre otra). */
    public static synchronized void reiniciar() {
        cerrarFisica();
    }

    private static void cerrarFisica() {
        if (fisica != null) {
            try {
                fisica.close();
            } catch (SQLException ignorada) {
                // ya estaba caída
            }
        }
        fisica = null;
        compartida = null;
    }

    /** SQLState 08xxx o excepción recuperable: falló la comunicación, no la consulta. */
    public static boolean esFalloDeConexion(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLRecoverableException
                    || t instanceof SQLTransientConnectionException
                    || t instanceof SQLNonTransientConnectionException) {
                return true;
            }
            if (t instanceof SQLException sq && sq.getSQLState() != null
                    && sq.getSQLState().startsWith("08")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Para los catch del DAO: si el error es de conexión, descarta la conexión y lanza
     * {@link FalloBaseDatos} (así no se confunde con "identificador no declarado").
     * Cualquier otro error SQL se deja al manejo que ya tenía cada método.
     */
    public static void verificarFallo(SQLException e, String operacion) {
        if (esFalloDeConexion(e)) {
            reiniciar();
            throw new FalloBaseDatos("Fallo de conexión con la base de datos al " + operacion
                    + ": " + e.getMessage(), e);
        }
    }
    public boolean limpiarTablaSimbolos() {

        String sql = "DELETE FROM tabla_simbolos";

        try (Connection conexion = ConexionBD.conectar();
            PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.executeUpdate();

            System.out.println("Tabla de símbolos limpiada correctamente.");

            return true;

        } catch (SQLException e) {

            System.err.println("Error al limpiar la tabla de símbolos.");
            e.printStackTrace();

            return false;
        }
    }   
    
}