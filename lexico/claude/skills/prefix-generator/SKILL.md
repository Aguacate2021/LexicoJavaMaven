# Prefix Generator

Esta Skill se utiliza cuando el usuario solicita trabajar en la generación de operaciones del compilador.

## Objetivo

Generar expresiones en NOTACIÓN PREFIJA / POLACA.

El operador debe aparecer antes de sus operandos.

Ejemplo:

Entrada:

35 + 5 * 8

Salida:

+ 35 * 5 8

NO generar:

35 5 8 * +

---

## Procedimiento

Antes de modificar código:

1. Buscar dónde se construyen las operaciones.
2. Buscar los marcadores relacionados con operaciones.
3. Revisar las producciones sintácticas.
4. Revisar la precedencia de operadores.
5. Revisar cómo se almacenan operadores y operandos.
6. Determinar dónde se genera actualmente la salida.
7. Identificar el primer punto donde la salida actual difiere de la esperada.

---

## Regla de precedencia

Las operaciones internas deben conservar la precedencia definida por el lenguaje.

Por ejemplo:

35 + 5 * 8

Debe producir:

+ 35 * 5 8

porque la multiplicación se encuentra dentro del segundo operando de la suma.

---

## Dos pilas

Si el algoritmo utiliza:

* pila de operadores;
* pila de operandos;

mantener esta estructura salvo que exista una razón demostrable para cambiarla.

---

## Salida

Cada expresión debe conservar su línea de origen.

Formato:

Linea N: <expresión prefija>

---

## Diagnóstico

Cuando exista una diferencia:

### 1. Comparar

SALIDA ACTUAL

vs.

SALIDA ESPERADA

### 2. Encontrar

El primer token diferente.

### 3. Seguir

La operación desde la producción sintáctica hasta la generación final.

### 4. Corregir

El punto donde se pierde el orden de la operación.

No modificar arbitrariamente el resultado final para que coincida.

---

## Restricciones

No convertir la solución a notación postfija.

No reescribir todo sintaxis.java.

No modificar el lexer.

No modificar los códigos de tokens.

No cambiar la gramática salvo que sea necesario y esté justificado.

No eliminar marcadores 801/802 sin determinar primero su función.

---

## Verificación

Después de realizar cambios:

1. Compilar.
2. Ejecutar.
3. Generar salida.
4. Comparar con ejemplos conocidos.
5. Reportar diferencias restantes.
