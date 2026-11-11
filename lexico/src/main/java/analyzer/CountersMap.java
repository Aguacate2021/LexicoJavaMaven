package analyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Semántica 3: tabla de contadores por función y parámetro
 * (Función | Entradas | Salida | Aceptados | Errores). Sin columna "Uso".
 */
public class CountersMap {

    public static final String[] ENCABEZADOS = { "Función", "Entradas", "Salida", "Aceptados", "Errores" };

    /** Contadores de una función (pow) o de uno de sus parámetros (pow_p1). */
    public static class CounterItem {
        public final String funcion;
        public int entradas;
        public int salida;
        public int aceptados;
        public int errores;

        CounterItem(String funcion) {
            this.funcion = funcion;
        }

        public String[] columnas() {
            return new String[] { funcion, String.valueOf(entradas), String.valueOf(salida),
                    String.valueOf(aceptados), String.valueOf(errores) };
        }
    }

    private final Map<String, CounterItem> mapa = new LinkedHashMap<>();

    public CountersMap() {
        limpiar();
    }

    /** Reinicia en cero todas las funciones y parámetros del catálogo (aparecen aunque no se usen). */
    public final void limpiar() {
        mapa.clear();
        for (FuncionNativa f : FuncionNativa.values()) {
            item(f.nombre);
            for (int i = 1; i <= f.parametros.length; i++) {
                item(f.nombreParametro(i));
            }
        }
    }

    private CounterItem item(String clave) {
        return mapa.computeIfAbsent(clave, CounterItem::new);
    }

    /** Evaluación de un parámetro (columna Entradas). */
    public void contarEntrada(String clave, boolean aceptado) {
        CounterItem c = item(clave);
        c.entradas++;
        if (aceptado) c.aceptados++; else c.errores++;
    }

    /** Evaluación del retorno de la función (columna Salida). */
    public void contarSalida(String clave, boolean aceptado) {
        CounterItem c = item(clave);
        c.salida++;
        if (aceptado) c.aceptados++; else c.errores++;
    }

    public CounterItem get(String clave) {
        return mapa.get(clave);
    }

    /** Filas ordenadas alfabéticamente (como la hoja SEMANTICA 3 T2). */
    public List<CounterItem> items() {
        return new ArrayList<>(new TreeMap<>(mapa).values());
    }

    public List<String[]> filas() {
        List<String[]> r = new ArrayList<>();
        for (CounterItem c : items()) {
            r.add(c.columnas());
        }
        return r;
    }
}
