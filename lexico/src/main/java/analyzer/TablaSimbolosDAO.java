package analyzer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TablaSimbolosDAO {

    /**
     * Verifica si un identificador ya existe
     * dentro de un ámbito específico.
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

            int filas = ps.executeUpdate();

            if (filas > 0) {
                System.out.println(
                    "[BD] INSERTADO -> " +
                    "id=" + id +
                    " | tipo=" + tipo +
                    " | clase=" + clase +
                    " | amb=" + ambito
                );

                return true;
            }

        } catch (SQLException e) {

            System.err.println(
                    "[BD] No se pudo insertar el identificador: "
                    + id
            );

            e.printStackTrace();

            return false;
        }

        return false;
    }

    /**
     * Actualiza la información de un arreglo.
     */
    public boolean actualizarArreglo(
            String idArreglo,
            int ambito,
            String tamañoArreglo,
            int dimensionesArreglo) {

        String sql = """
            UPDATE tabla_simbolos
            SET tarr = ?,
                dimarr = ?,
                clase = 'Arreglo'
            WHERE id = ?
              AND amb = ?
            """;

        try (Connection conexion = ConexionBD.conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, tamañoArreglo);
            ps.setInt(2, dimensionesArreglo);
            ps.setString(3, idArreglo);
            ps.setInt(4, ambito);

            int filas = ps.executeUpdate();

            if (filas > 0) {

                System.out.println(
                    "[BD] ARREGLO ACTUALIZADO -> " +
                    "id=" + idArreglo +
                    " | tamaño=" + tamañoArreglo +
                    " | dimensiones=" + dimensionesArreglo +
                    " | amb=" + ambito
                );

                return true;
            }

            System.err.println(
                "[BD] No se encontró el arreglo para actualizar: "
                + idArreglo
            );

            return false;

        } catch (SQLException e) {

            System.err.println(
                    "Error al actualizar arreglo: "
                    + idArreglo
            );

            e.printStackTrace();

            return false;
        }
    }

    /**
     * Actualiza el TParr de una función con el ámbito real
     * que el parser asignó a su cuerpo/lista de parámetros
     * (el que abre AbrirAmbito() justo después de declararla).
     * No se calcula con una fórmula: se guarda el valor real
     * que reportó el parser.
     */
    public boolean actualizarTParFuncion(
            String idFuncion,
            int ambito,
            int ambitoHijo) {

        String sql = """
            UPDATE tabla_simbolos
            SET tpar = ?
            WHERE id = ?
              AND amb = ?
            """;

        try (Connection conexion = ConexionBD.conectar();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, String.valueOf(ambitoHijo));
            ps.setString(2, idFuncion);
            ps.setInt(3, ambito);

            int filas = ps.executeUpdate();

            if (filas > 0) {

                System.out.println(
                    "[BD] TPAR ACTUALIZADO -> " +
                    "id=" + idFuncion +
                    " | amb=" + ambito +
                    " | tpar(ambito hijo)=" + ambitoHijo
                );

                return true;
            }

            System.err.println(
                "[BD] No se encontró la función para actualizar tpar: "
                + idFuncion
            );

            return false;

        } catch (SQLException e) {

            System.err.println(
                    "Error al actualizar tpar de función: "
                    + idFuncion
            );

            e.printStackTrace();

            return false;
        }
    }

    /**
     * Actualiza el número de parámetros de una función.
     */
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

            int filas = ps.executeUpdate();

            if (filas > 0) {

                System.out.println(
                    "[BD] FUNCION ACTUALIZADA -> " +
                    "id=" + idFuncion +
                    " | parámetros=" + numParametros +
                    " | amb=" + ambito
                );

                return true;
            }

            System.err.println(
                "[BD] No se encontró la función para actualizar: "
                + idFuncion
            );

            return false;

        } catch (SQLException e) {

            System.err.println(
                    "Error al actualizar función: "
                    + idFuncion
            );

            e.printStackTrace();

            return false;
        }
    }

    /**
     * Limpia toda la tabla de símbolos.
     */
    public boolean limpiarTablaSimbolos() {

        String sql = "DELETE FROM tabla_simbolos";

        try (Connection conexion = ConexionBD.conectar();
             PreparedStatement ps =
                     conexion.prepareStatement(sql)) {

            int filas = ps.executeUpdate();

            System.out.println(
                "[BD] Tabla de símbolos limpiada. " +
                "Registros eliminados: " + filas
            );

            return true;

        } catch (SQLException e) {

            System.err.println(
                "[BD] Error al limpiar la tabla de símbolos."
            );

            e.printStackTrace();

            return false;
        }
    }

    /**
     * Método de prueba.
     */
    public static void probarInsercion() {

        String sql = """
            INSERT INTO tabla_simbolos
            (id, tipo, clase, amb, tarr, dimarr, nopar, tpar)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conexion = ConexionBD.conectar();
             PreparedStatement ps =
                     conexion.prepareStatement(sql)) {

            ps.setString(1, "$x");
            ps.setString(2, "variable");
            ps.setString(3, "entero");
            ps.setInt(4, 0);
            ps.setString(5, null);
            ps.setString(6, null);
            ps.setInt(7, 0);
            ps.setString(8, null);

            ps.executeUpdate();

            System.out.println(
                "[BD] INSERT de prueba realizado correctamente."
            );

        } catch (SQLException e) {

            System.err.println(
                "[BD] Error en INSERT de prueba."
            );

            e.printStackTrace();
        }
    }
}