package analyzer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TablaSimbolosDAO {

    /**
     * Verifica si un identificador ya existe
     * dentro de un ámbito específico.
     *
     * @param id identificador
     * @param ambito ámbito donde se busca
     * @return true si existe, false si no existe
     */
    public boolean idExiste(String id, int ambito) {

        String sql = """
            SELECT COUNT(*)
            FROM tabla_simbolos
            WHERE id = ?
              AND amb = ?
            """;

        try (Connection conexion = ConexionBD.conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, id);
            ps.setInt(2, ambito);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {

            System.err.println(
                    "Error al verificar identificador: " + id
            );

            e.printStackTrace();
        }

        return false;
    }

    /**
     * Inserta un identificador en la tabla de símbolos.
     */
    public boolean insertar(
            String id,
            String tipo,
            String clase,
            int ambito,
            String tarr,
            String dimarr,
            Integer nopar,
            String tpar) {

        String sql = """
            INSERT INTO tabla_simbolos
            (id, tipo, clase, amb, tarr, dimarr, nopar, tpar)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conexion = ConexionBD.conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, id);
            ps.setString(2, tipo);
            ps.setString(3, clase);
            ps.setInt(4, ambito);
            ps.setString(5, tarr);
            ps.setString(6, dimarr);

            if (nopar == null) {
                ps.setNull(7, java.sql.Types.INTEGER);
            } else {
                ps.setInt(7, nopar);
            }

            ps.setString(8, tpar);

            ps.executeUpdate();

            return true;

        } catch (SQLException e) {

            System.err.println(
                    "No se pudo insertar el identificador: " + id
            );

            e.printStackTrace();

            return false;
        }
    }
    public boolean actualizarArreglo(
        String idArreglo,
        int ambito,
        String tamañoArreglo,
        int dimensionesArreglo) {

    String sql = """
        UPDATE tabla_simbolos
        SET tarr = ?,
            dimarr = ?
        WHERE id = ?
          AND amb = ?
        """;

    try (Connection conexion = ConexionBD.conectar();
         PreparedStatement ps = conexion.prepareStatement(sql)) {

        ps.setString(1, tamañoArreglo);
        ps.setInt(2, dimensionesArreglo);
        ps.setString(3, idArreglo);
        ps.setInt(4, ambito);

        return ps.executeUpdate() > 0;

    } catch (SQLException e) {
        System.err.println("Error al actualizar arreglo: " + idArreglo);
        e.printStackTrace();
        return false;
    }
}
    public boolean actualizarFuncion(
        String idFuncion,
        int ambito,
        int numParametros) {

    String sql = """
        UPDATE tabla_simbolos
        SET nopar = ?
        WHERE id = ?
          AND amb = ?
        """;

    try (Connection conexion = ConexionBD.conectar();
         PreparedStatement ps = conexion.prepareStatement(sql)) {

        ps.setInt(1, numParametros);
        ps.setString(2, idFuncion);
        ps.setInt(3, ambito);

        return ps.executeUpdate() > 0;

    } catch (SQLException e) {
        System.err.println("Error al actualizar función: " + idFuncion);
        e.printStackTrace();
        return false;
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
    public static void probarInsercion() {

    String sql = """ 
        INSERT INTO tabla_simbolos
        (id, tipo, clase, amb, tarr, dimarr, nopar, tpar)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

    try (Connection conexion = ConexionBD.conectar();
         PreparedStatement ps = conexion.prepareStatement(sql)) {

        ps.setString(1, "$x");
        ps.setString(2, "variable");
        ps.setString(3, "entero");
        ps.setInt(4, 0);
        ps.setString(5, null);
        ps.setString(6, null);
        ps.setInt(7, 0);
        ps.setString(8, null);

        ps.executeUpdate();

        System.out.println("INSERT realizado correctamente.");

    } catch (SQLException e) {
        e.printStackTrace();
    }
}
}