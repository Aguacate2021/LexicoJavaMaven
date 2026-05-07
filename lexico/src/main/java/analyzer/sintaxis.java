package analyzer;

import java.util.List;
import java.util.Stack;

public class sintaxis {
    private final static Lexer lexer = new Lexer();
    public void parsear(List<Token> tokens) {
        Stack<Integer> pilaProducciones = new Stack<>();
        pilaProducciones.push(0); // Estado inicial
            Token tokenActual = tokens.get(0);
            int tokenClass = tokenActual.getTokenClass();
            int fila = pilaProducciones.peek();
            int columna = LeerCSV2.clasificarTransicion(tokenClass);
        while (!tokens.isEmpty() && !pilaProducciones.isEmpty()) {
            System.out.println(LeerCSV2.getValor(fila, columna));
            if(pilaProducciones.peek()>=0&&tokenActual.getTokenClass()<0){
                if(EstadoTabla>0&&EstadoTabla!=147){
                    pilaProducciones=Producciones.aplicarProduccion(pilaProducciones, EstadoTabla);
                    fila = pilaProducciones.peek();
                }
                if(EstadoTabla==147){
                    pilaProducciones.pop();
                }
                if(EstadoTabla>=512){
                    System.out.println("Error sintáctico en token: " + tokenActual.getLexema() + " en línea " + tokenActual.getLinea() + ", columna " + tokenActual.getColumna());
                }
                int EstadoTabla = LeerCSV2.getValor(fila, columna);
            }
            if(pilaProducciones.peek()<0&&tokenActual.getTokenClass()<0&&pilaProducciones.peek()==tokenActual.getTokenClass()){
                pilaProducciones.pop();
                tokens.remove(0);
            }
            if((pilaProducciones.peek()<0)&&(tokenActual.getTokenClass()<0)&&(pilaProducciones.peek()!=tokenActual.getTokenClass())){
                System.out.println("Error sintáctico en token: " + tokenActual.getLexema() + " en línea " + tokenActual.getLinea() + ", columna " + tokenActual.getColumna());
                break;
            }
            System.out.println("Pila: " + pilaProducciones);
            System.out.println("Token actual: " + tokenActual);
        }
        
    }
    public static void main(String[] args) {
            sintaxis parser = new sintaxis();
            List<Token> tokens = lexer.tokenizar("main(){}");
            parser.parsear(tokens);
    }
}
