package analyzer;

import java.util.List;

public class ContadorTokens {

    // 🔷 GENERALES
    public int operadores = 0;
    public int comentarios = 0;
    public int constantes = 0;
    public int identificadores = 0;
    public int reservadas = 0;
    public int booleanos = 0;
    public int nulos = 0;
    public int errores = 0;

    
    public int idCadena = 0;
    public int idBinario = 0;
    public int idDecimal = 0;
    public int idOctal = 0;
    public int idHex = 0;
    public int idReal = 0;
    public int idExp = 0;
    public int idBool = 0;

    
    public int cteCadena = 0;
    public int cteBinario = 0;
    public int cteDecimal = 0;
    public int cteOctal = 0;
    public int cteHex = 0;
    public int cteReal = 0;
    public int cteExp = 0;
    public int cteBool = 0;
    public int cteNull = 0;

    
    public int opPostfix = 0;
    public int opLogBin = 0;
    public int opControl = 0;
    public int opMat = 0;
    public int opExp = 0;
    public int opTurno = 0;
    public int opRel = 0;
    public int opIgualdad = 0;
    public int opLogicos = 0;
    public int opTernario = 0;
    public int opAsignacion = 0;
    public int opAgrup = 0;

    public void contar(List<Token> tokens) {

        for (Token t : tokens) {
            int token = (int) t.getEstado();

            
            if (token >= -50 && token <= -1) {
                operadores++;

                if (token >= -2 && token <= -1) opPostfix++;
                else if (token >= -6 && token <= -3) opLogBin++;
                else if (token >= -10 && token <= -7) opControl++;
                else if (token >= -15 && token <= -11) opMat++;
                else if (token == -106) opMat++;
                else if (token == -16) opExp++;
                else if (token >= -19 && token <= -17) opTurno++;
                else if (token >= -26 && token <= -20) opRel++;
                else if (token == -27 || token == -28) opIgualdad++;
                else if (token >= -31 && token <= -29) opLogicos++;
                else if (token == -32) opTernario++;
                else if (token >= -44 && token <= -33) opAsignacion++;
                else if (token >= -50 && token <= -45) opAgrup++;
            }

            
            else if (token >= -52 && token <= -51) {
                comentarios++;
            }

            
            else if (token >= -59 && token <= -53) {
                constantes++;

                if (token == -53) cteCadena++;
                else if (token == -54) cteBinario++;
                else if (token == -55) cteDecimal++;
                else if (token == -56) cteOctal++;
                else if (token == -57) cteHex++;
                else if (token == -58) cteReal++;
                else if (token == -59) cteExp++;
            }

            
            else if (token >= -67 && token <= -60) {
                identificadores++;

                if (token == -60) idCadena++;
                else if (token == -61) idBinario++;
                else if (token == -62) idDecimal++;
                else if (token == -63) idOctal++;
                else if (token == -64) idHex++;
                else if (token == -65) idReal++;
                else if (token == -66) idExp++;
                else if (token == -67) idBool++;
            }

            
            else if (token == -74) {
                nulos++;
                cteNull++;
            }
            else if (token == -72 || token == -73) {
                booleanos++;
                cteBool++;
            }

            else if (token >= -105 && token <= -68) {
                reservadas++;
            } 
            else {
                errores++;
            }
        }
    }
}