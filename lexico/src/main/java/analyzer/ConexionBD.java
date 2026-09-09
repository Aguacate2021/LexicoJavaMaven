package analyzer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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

    /**
     * Obtiene una conexión a la base de datos.
     */
    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(
                URL,
                USUARIO,
                PASSWORD
        );
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