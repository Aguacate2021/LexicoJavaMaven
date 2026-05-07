package analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class Producciones {
    // Mapa de producciones
    private static final HashMap<Integer, List<Integer>> producciones = new HashMap<>();

    static {

        // Producción 0 -> 0
        producciones.put(0, Arrays.asList(0));

        // Producción 1 -> 1 -68 -49 -50 -45 24 2 -46
        producciones.put(1, Arrays.asList(
                1, -68, -49, -50, -45, 24, 2, -46
        ));

        // Producción 2 -> ε
        producciones.put(2, new ArrayList<>());

        // Producción 3 -> -9 24 2
        producciones.put(3, Arrays.asList(
                -9, 24, 2
        ));

        // Producción 4 -> ε
        producciones.put(4, new ArrayList<>());

        // Producción 5
        producciones.put(5, Arrays.asList(
                -69, -1000, -45, -1000, 3, -46, 1
        ));

        // Producción 6
        producciones.put(6, Arrays.asList(
                -70, 4, -9
        ));

        // Producción 7
        producciones.put(7, Arrays.asList(
                -71, -1000, 12, 0, -9, 1
        ));

        // Producción 8
        producciones.put(8, Arrays.asList(
                -1000, -33, 11, 8, -9, 1
        ));

        // Producción 9 -> ε
        producciones.put(9, new ArrayList<>());


        producciones.put(10, Arrays.asList(
                -7, -1000, 3
        ));

        producciones.put(11, Arrays.asList(
                -69, -1000, -1000, -47, -55,
                5, -48, 6, 7, -9, 1
        ));
        producciones.put(12, Arrays.asList(
                -1000, -47, -55, 5, -48,
                6, 7, -9, 1
        ));
        producciones.put(13, new ArrayList<>());
        producciones.put(14, Arrays.asList(
                -7, -55, 5
        ));
        producciones.put(15, new ArrayList<>());
        producciones.put(16, Arrays.asList(
        -47, -55, 5, -48, 6
        ));

        producciones.put(18, Arrays.asList(
                -7, 4
        ));

        producciones.put(20, Arrays.asList(
                -7, -1000, -33, 11, 8
        ));

        producciones.put(21, Arrays.asList(
                -54
        ));

        producciones.put(22, Arrays.asList(
                -55
        ));

        producciones.put(23, Arrays.asList(
                -56
        ));

        producciones.put(24, Arrays.asList(
                -57
        ));

        producciones.put(25, Arrays.asList(
                -58
        ));

        producciones.put(26, Arrays.asList(
                -53
        ));

        producciones.put(27, Arrays.asList(
                9
        ));

        producciones.put(28, Arrays.asList(
                -72
        ));

        producciones.put(29, Arrays.asList(
                -73
        ));

        producciones.put(30, Arrays.asList(
                -59
        ));

        producciones.put(31, Arrays.asList(
                -74
        ));

        producciones.put(32, Arrays.asList(
                -11, 10
        ));

        producciones.put(33, Arrays.asList(
                -12, 10
        ));

        producciones.put(34, Arrays.asList(
                10
        ));

        producciones.put(35, Arrays.asList(
                -49, -1000, 13, -50
        ));

        producciones.put(36, Arrays.asList(
                -7, -1000, 13
        ));

        producciones.put(38, Arrays.asList(
                -33
        ));

        producciones.put(39, Arrays.asList(
                -34
        ));

        producciones.put(40, Arrays.asList(
                -35
        ));

        producciones.put(41, Arrays.asList(
                -37
        ));

        producciones.put(42, Arrays.asList(
                -36
        ));

        producciones.put(43, Arrays.asList(
                11
        ));

        producciones.put(44, Arrays.asList(
                -1000, 16
        ));

        producciones.put(45, Arrays.asList(
                -1, -1000, 16
        ));

        producciones.put(46, Arrays.asList(
                -2, -1000, 16
        ));

        producciones.put(47, Arrays.asList(
                -29, -49, 35, -50
        ));

        producciones.put(48, Arrays.asList(
                -3, -49, 35, -50
        ));

        producciones.put(49, Arrays.asList(
                -49, 35, -50
        ));

        producciones.put(50, Arrays.asList(
                21
        ));

        producciones.put(52, Arrays.asList(
                -49, 19, -50
        ));

        producciones.put(53, Arrays.asList(
                22, 17
        ));

        producciones.put(54, Arrays.asList(
                14, 35, 18
        ));

        producciones.put(56, Arrays.asList(
                14, 35, 18
        ));

        producciones.put(58, Arrays.asList(
                -32, 35, -10, 35
        ));

        producciones.put(60, Arrays.asList(
                35, 20
        ));

        producciones.put(62, Arrays.asList(
                -7, 35, 20
        ));

        producciones.put(63, Arrays.asList(
                -75
        ));

        producciones.put(64, Arrays.asList(
                -76, -49, 35, -50
        ));

        producciones.put(65, Arrays.asList(
                -77, -49, 35, -7, 35, -50
        ));

        producciones.put(66, Arrays.asList(
                -78, -49, 35, -7, 35, -50
        ));

        producciones.put(67, Arrays.asList(
                -79, -49, 35, -50
        ));

        producciones.put(68, Arrays.asList(
                -80, -49, 35, -50
        ));

        producciones.put(69, Arrays.asList(
                -81, -49, 35, -7, 35, -50
        ));

        producciones.put(70, Arrays.asList(
                -82, -49, 35, -7, 35, -7, 35, -50
        ));

        producciones.put(71, Arrays.asList(
                -83, -49, 35, -7, 35, -50
        ));

        producciones.put(72, Arrays.asList(
                -84, -49, 35, -50
        ));

        producciones.put(73, Arrays.asList(
                -85, -49, 35, -50
        ));

        producciones.put(74, Arrays.asList(
                -86, -49, 35, -50
        ));

        producciones.put(75, Arrays.asList(
                -87, -49, 35, -50
        ));

        producciones.put(76, Arrays.asList(
                -88, -49, 35, -50
        ));

        producciones.put(77, Arrays.asList(
                -89, -49, 35, -50
        ));

        producciones.put(78, Arrays.asList(
                -90, -49, 35, -50
        ));

        producciones.put(79, Arrays.asList(
                -91, -49, 35, -50
        ));

        producciones.put(80, Arrays.asList(
                -92, -49, 35, -50
        ));

        producciones.put(81, Arrays.asList(
                -47, 35, 23, -48
        ));

        producciones.put(82, Arrays.asList(
                -7, 35, 23
        ));

        producciones.put(84, Arrays.asList(
                -93, -49, 35, 25, -50
        ));

        producciones.put(86, Arrays.asList(
                -94, -49, 35, -50
        ));

        producciones.put(87, Arrays.asList(
                -105, -49, 35, -50, 24, 26
        ));

        producciones.put(88, Arrays.asList(
                35
        ));

        producciones.put(89, Arrays.asList(
                -45, 24, 27, -46
        ));

        producciones.put(90, Arrays.asList(
                -95, -49, 35, -50, 24
        ));

        producciones.put(91, Arrays.asList(
                -96, 24, -95, -49, 35, -50
        ));

        producciones.put(92, Arrays.asList(
                -97, 35
        ));

        producciones.put(93, Arrays.asList(
                -98, -49, 35, 28
        ));

        producciones.put(94, Arrays.asList(
                -99, -49, 35, -50, -45, 30, -46
        ));

        producciones.put(96, Arrays.asList(
                -7, 35
        ));

        producciones.put(98, Arrays.asList(
                -100, -49, 35, -50, 24, 26
        ));

        producciones.put(99, Arrays.asList(
                -101, 24
        ));

        producciones.put(101, Arrays.asList(
                -9, 24, 27
        ));

        producciones.put(102, Arrays.asList(
                -7, 35, 28
        ));

        producciones.put(103, Arrays.asList(
                -10, 35, -50, 24
        ));

        producciones.put(104, Arrays.asList(
                -9, 24, -9, 35, 29, -50, 24
        ));

        producciones.put(106, Arrays.asList(
                -7, 35, 29
        ));

        producciones.put(107, Arrays.asList(
                -102, 35, -10, 24, 31, -104, 32
        ));

        producciones.put(108, Arrays.asList(
                -9, 24, 31
        ));

        producciones.put(110, Arrays.asList(
                30
        ));

        producciones.put(111, Arrays.asList(
                33
        ));

        producciones.put(113, Arrays.asList(
                -103, -10, 24, 34
        ));

        producciones.put(114, Arrays.asList(
                -9, 24
        ));

        producciones.put(116, Arrays.asList(
                37, 36
        ));

        producciones.put(118, Arrays.asList(
                -31, 37, 36
        ));

        producciones.put(119, Arrays.asList(
                -4, 37, 36
        ));

        producciones.put(120, Arrays.asList(
                39, 38
        ));

        producciones.put(122, Arrays.asList(
                -30, 39, 38
        ));

        producciones.put(123, Arrays.asList(
                -5, 39, 38
        ));

        producciones.put(124, Arrays.asList(
                41, 40
        ));

        producciones.put(126, Arrays.asList(
                -20, 41, 40
        ));

        producciones.put(127, Arrays.asList(
                -23, 41, 40
        ));

        producciones.put(128, Arrays.asList(
                -22, 41, 40
        ));

        producciones.put(129, Arrays.asList(
                -25, 41, 40
        ));

        producciones.put(130, Arrays.asList(
                -24, 41, 40
        ));

        producciones.put(131, Arrays.asList(
                -21, 41, 40
        ));

        producciones.put(132, Arrays.asList(
                43, 42
        ));

        producciones.put(134, Arrays.asList(
                -12, 43, 42
        ));

        producciones.put(135, Arrays.asList(
                -11, 43, 42
        ));

        producciones.put(136, Arrays.asList(
                -17, 43, 42
        ));

        producciones.put(137, Arrays.asList(
                -18, 43, 42
        ));

        producciones.put(138, Arrays.asList(
                -19, 43, 42
        ));

        producciones.put(139, Arrays.asList(
                45, 44
        ));

        producciones.put(141, Arrays.asList(
                -13, 45, 44
        ));

        producciones.put(142, Arrays.asList(
                -14, 45, 44
        ));

        producciones.put(143, Arrays.asList(
                -106, 45, 44
        ));

        producciones.put(144, Arrays.asList(
                -15, 45, 44
        ));

        producciones.put(145, Arrays.asList(
                15, 46
        ));

        producciones.put(146, Arrays.asList(
                -6, 15, 46
        ));
        // Producción 147 -> ε
        producciones.put(147, new ArrayList<>());
    }

    // =========================================================
    // MÉTODO PARA INSERTAR EN PILA DE DERECHA A IZQUIERDA
    // =========================================================
    public static Stack<Integer> aplicarProduccion(Stack<Integer> pila, int numProduccion) {

        List<Integer> produccion = producciones.get(numProduccion);
        pila.pop(); // Eliminar el símbolo no terminal que se va a expandir
        if (produccion == null) {
            System.out.println("Producción no encontrada");
            return pila;
        }
        if (produccion.isEmpty()) {
            return pila;
        }
        // Insertar de derecha a izquierda
        for (int i = produccion.size() - 1; i >= 0; i--) {

            int simbolo = produccion.get(i);

            pila.push(simbolo);
        }
        return pila;
    }

    /* 
    // =========================================================
    // EJEMPLO
    // =========================================================
    public static void main(String[] args) {

        Stack<Integer> pila = new Stack<>();

        aplicarProduccion(pila, 1);

        System.out.println("Contenido pila:");

        while (!pila.isEmpty()) {
            System.out.println(pila.pop());
        }
    }*/
}
