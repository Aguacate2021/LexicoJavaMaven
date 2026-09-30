# Estado actual del proyecto

## Proyecto

AnalyzerIDE / SintaxisJavaMaven

## Lenguaje

Java

## Build

Maven

---

# Objetivo actual

Avance 1: Generador de Prefijo.

El objetivo es generar las operaciones del código fuente en notación prefija/polaca.

---

# Importante

La salida requerida es PREFIJA.

Ejemplo:

+ 35 * 5 8

NO:

35 5 8 * +

---

# Salida esperada conocida

Ejemplo:

Linea 13: = #DX && < - + 35 * #XT 8 / ...

Linea 29: = #DX && < - + 35 * 5 8 / ...

Linea 47: = $X - + 35 * ¡Q 8 ...

Linea 53: = #DX - + 35 * ¡Q ...

Estas salidas deben utilizarse como referencia al modificar el generador.

---

# Componentes existentes

## Análisis léxico

Ya existe.

## Análisis sintáctico

Ya existe.

Archivo importante:

sintaxis.java

## Tabla de símbolos

Base de datos:

Ambito_23130243

Tabla:

tabla_simbolos

## Generación de operaciones

Actualmente se trabaja sobre la etapa final del análisis sintáctico.

Se consideran marcadores existentes dentro de las producciones, incluyendo:

801
802

No eliminar estos marcadores sin comprobar primero su función.

---

# Reglas que NO deben romperse

* No cambiar códigos de tokens.
* No modificar innecesariamente el lexer.
* No modificar innecesariamente la tabla de símbolos.
* No cambiar las reglas de ámbitos.
* No convertir la generación a postfix.
* No eliminar marcadores existentes sin analizarlos.
* No reemplazar sintaxis.java completo si no es necesario.

---

# Último problema conocido

La generación de operaciones debe producir exactamente el orden requerido por la notación prefija y respetar la precedencia de operadores.

---

# Prueba mínima

Para cualquier modificación del generador:

1. Compilar.
2. Ejecutar el compilador.
3. Procesar una entrada conocida.
4. Generar el archivo de operaciones.
5. Comparar las líneas generadas con las líneas esperadas.

---

# Registro de cambios

## 2026-09-29

Se creó esta documentación para mantener el contexto del proyecto.
