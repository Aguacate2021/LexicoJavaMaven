package analyzer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

public class sintaxis {

    private static final boolean TRACE = true;
    private static final int MAX_ERRORES = 200;

    private final List<ErrorEntry> erroresSintaxis = new ArrayList<>();

    public List<ErrorEntry> getErroresSintaxis() {
        return erroresSintaxis;
    }

    public void parsear(List<Token> tokens) {

        erroresSintaxis.clear();
        ContadorCiclos.resetearContadores();
        LeerCSV2.LeerCSV();

        LinkedList<Token> lt = new LinkedList<>(tokens);
        Stack<Integer> ps = new Stack<>();
        ps.push(0);

        while (!lt.isEmpty() && !ps.isEmpty()) {

            if (ContadorCiclos.ERRORES >= MAX_ERRORES) {
                log("Límite de errores alcanzado, abortando análisis.");
                break;
            }

            Token tokenActual = lt.getFirst();
            int cima = ps.peek();

            log("Cima: " + cima + " | Token: " + tokenActual.getLexema()
                    + " (clase " + tokenActual.getTokenClass()
                    + ") ln:" + tokenActual.getLinea());

            if (cima >= 0) {
                // ── NO TERMINAL ──────────────────────────────────────────────
                int columna = LeerCSV2.clasificarTransicion(tokenActual.getTokenClass());

                if (columna < 0) {
                    // token no clasificable → error, consumir token
                    registrarError(tokenActual, "Token no reconocido por la tabla sintáctica");
                    lt.removeFirst();
                    continue;
                }

                int resultado = LeerCSV2.getValor(cima, columna);

                if (resultado >= 512) {
                    // ── ERROR DE NO TERMINAL ──────────────────────────────────
                    registrarError(tokenActual,
                            "Error sintáctico: no hay producción para NT=" + cima
                            + " con token=" + tokenActual.getLexema());
                    lt.removeFirst();
                    // NO se hace pop: el NT permanece para intentar con el siguiente token

                } else if (resultado == 147) {
                    // ── EPSILON ───────────────────────────────────────────────
                    ps.pop();
                    ContadorCiclos.aumentarContador(cima);
                    log("Epsilon: NT=" + cima + " derivó en ε");

                } else {
                    // ── PRODUCCIÓN ────────────────────────────────────────────
                    ps.pop();
                    ContadorCiclos.aumentarContador(cima);
                    Producciones.aplicarProduccion(ps, resultado);
                    log("NT=" + cima + " → producción " + resultado);
                }

            } else {
                // ── TERMINAL ─────────────────────────────────────────────────
                if (cima == -1000) {
                    // caso especial: identificador genérico
                    int tc = tokenActual.getTokenClass();
                    if (tc >= -67 && tc <= -60) {
                        ps.pop();
                        lt.removeFirst();
                        log("Match ID: " + tokenActual.getLexema());
                    } else {
                        registrarError(tokenActual,
                                "Se esperaba un identificador, se encontró: "
                                + tokenActual.getLexema());
                        lt.removeFirst();
                    }

                } else if (cima == tokenActual.getTokenClass()) {
                    // match normal
                    ps.pop();
                    lt.removeFirst();
                    log("Match: " + tokenActual.getLexema());

                } else {
                    // error de terminal: consumir token y continuar (no break)
                    registrarError(tokenActual,
                            "Se esperaba terminal " + cima
                            + " pero se encontró " + tokenActual.getTokenClass()
                            + " ('" + tokenActual.getLexema() + "')");
                    break;
                }
            }
            log("Pila: " + ps);
        }

        // Vaciar epsilones residuales en la pila si los tokens ya se agotaron
        /*if (lt.isEmpty() && !ps.isEmpty()) {
            vaciarEpsilones(ps);
        }*/

        boolean exitoso = lt.isEmpty() && ps.isEmpty()&& ContadorCiclos.ERRORES == 0;
        if (exitoso) {
            System.out.println("\nAnálisis sintáctico correcto.");
        } else {
            System.out.println("\nAnálisis sintáctico finalizado con "
                    + ContadorCiclos.ERRORES + " error(es) sintáctico(s).");
        }
    }

    private void vaciarEpsilones(Stack<Integer> ps) {
        while (!ps.isEmpty()) {
            int cima = ps.peek();
            if (cima < 0&& cima>0) break; // queda un terminal no resuelto, dejar
            // intentar epsilon con columna 0 (EOF ficticio)
            int resultado = LeerCSV2.getValor( cima, 0);
            if (resultado == 147) {
                ps.pop();
                ContadorCiclos.aumentarContador(cima);
                log("Epsilon residual: NT=" + cima);
            } else {
                log("No se puede vaciar epsilon residual: NT=" + cima);
                break;
            }
        }
    }

    private void registrarError(Token t, String descripcion) {
        ContadorCiclos.ERRORES++;
        String codigo = String.format("ERR-SYN-%03d", ContadorCiclos.ERRORES);
        erroresSintaxis.add(new ErrorEntry(
                codigo,
                descripcion,
                t.getLinea(),
                "parser",
                ErrorEntry.Tipo.SINTAXIS,
                t.getLexema()
        ));
        System.out.println("[SYN-ERR] ln=" + t.getLinea()
                + " col=" + t.getColumna() + " | " + descripcion);
    }

    private void log(String msg) {
        if (TRACE) System.out.println("[TRACE] " + msg);
    }
}
