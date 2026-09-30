# AnalyzerIDE — Instrucciones del proyecto

## 1. Contexto

Este proyecto es un compilador/analizador desarrollado en Java para la materia Lenguajes y Autómatas I.

El proyecto contiene principalmente:

* Análisis léxico.
* Análisis sintáctico.
* Generación y procesamiento de ámbitos.
* Tabla de símbolos.
* Validación de identificadores.
* Registro de errores.
* Generación de operaciones en notación prefija.
* Exportación y lectura de información mediante Excel.
* Persistencia de información en MySQL.

El proyecto utiliza Java y Maven.

---

## 2. Regla principal: modificar lo mínimo necesario

Antes de modificar código:

1. Analiza cómo funciona actualmente.
2. Identifica exactamente dónde se encuentra el problema.
3. Modifica únicamente lo necesario.
4. Conserva la arquitectura existente.
5. No cambies nombres de clases, métodos, variables o tokens sin necesidad.
6. No reemplaces archivos completos si una modificación localizada es suficiente.
7. No hagas refactorizaciones generales que no hayan sido solicitadas.
8. No agregues dependencias innecesarias.
9. No elimines código funcional solamente porque podría hacerse de otra manera.

Si una solución requiere modificar varios archivos, explica primero qué archivos son necesarios y por qué.

---

# 3. Compilador

## Componentes importantes

El proyecto puede contener componentes relacionados con:

* Lexer / análisis léxico.
* Parser / análisis sintáctico.
* DFA.
* Tabla de símbolos.
* Control de ámbitos.
* Manejo de errores.
* Generación de operaciones.
* Archivos Excel.
* Base de datos MySQL.

Antes de modificar un componente, revisa cómo interactúa con los demás.

---

# 4. Tokens

Los tokens existentes son parte fundamental del proyecto.

NO cambiar códigos de tokens existentes sin que el usuario lo solicite explícitamente.

Ejemplos conocidos:

++ = -1
-- = -2
~  = -3
|  = -4
&  = -5
^  = -6
,  = -7
.  = -8
;  = -9
:  = -10

* = -11

- = -12

* = -13

También existen tokens especiales para:

* palabras reservadas
* identificadores
* errores léxicos
* errores sintácticos
* delimitadores de operaciones

No asumir nuevos códigos de token sin revisar primero cómo están definidos en el proyecto.

---

# 5. Ámbitos

El compilador maneja información por ámbito.

Cuando se solicite una búsqueda de identificadores:

* Revisar el ámbito actual.
* Respetar la estructura existente de ámbitos.
* No asumir que todos los identificadores pertenecen al ámbito global.
* Mantener las reglas existentes para parámetros y variables.

El error para un identificador no declarado debe conservar el formato:

"Identificador no declarado en el ámbito actual: ..."

Código de error asociado:

-3000

No cambiar este comportamiento sin una instrucción explícita.

---

# 6. Tabla de símbolos

La tabla de símbolos utiliza información relacionada con:

* id
* tipo
* Clase
* amb
* Tarr
* DimArr
* NoPar
* TParr

Antes de modificar la inserción o búsqueda de símbolos:

1. Revisar cómo se genera la información.
2. Revisar cómo se determina el ámbito.
3. Revisar la conexión con MySQL.
4. Revisar si el problema está en generación, inserción o consulta.

No solucionar un problema de BD modificando arbitrariamente el analizador sintáctico.

---

# 7. GENERACIÓN DE PREFIJO

## Regla crítica

La generación solicitada para este proyecto es NOTACIÓN PREFIJA / POLACA.

NO es notación postfija.

En notación prefija el operador aparece ANTES de sus operandos.

Ejemplo conceptual:

a + b

se representa como:

* a b

No generar:

a b +

---

## Ejemplo esperado del proyecto

Una salida válida puede tener una estructura como:

Linea 13: = #DX && < - + 35 * #XT 8 / ...

La salida debe conservar:

* número de línea
* operador
* operandos
* orden de operaciones
* identificadores
* constantes
* operadores originales

No reinterpretar automáticamente el formato como postfix.

---

# 8. Operadores y precedencia

Antes de modificar la generación de operaciones:

1. Revisar las producciones de sintaxis.
2. Identificar dónde se crean las operaciones.
3. Revisar los marcadores utilizados actualmente.
4. Revisar cómo se delimitan las expresiones.
5. Revisar la precedencia existente.
6. Revisar asociatividad cuando sea relevante.
7. Determinar qué información llega al generador.

No inventar una precedencia nueva si ya existe en el parser.

---

# 9. Pilas para generación

El proyecto puede utilizar dos pilas:

* pila de operandos
* pila de operadores

Si la implementación actual utiliza estas estructuras, conservarlas.

El objetivo es producir la expresión en NOTACIÓN PREFIJA.

Antes de cambiar el algoritmo, comprobar con una expresión pequeña.

Ejemplo:

35 + 5 * 8

Debe respetarse la precedencia de multiplicación:

+ 35 * 5 8

No:

* * 35 5 8

---

# 10. Líneas de origen

Cada operación generada debe conservar la línea de código fuente correspondiente.

Formato:

Linea N: <expresión prefija>

No eliminar el número de línea.

---

# 11. Marcadores de operaciones

El proyecto puede utilizar marcadores especiales dentro de las producciones sintácticas, por ejemplo:

801
802

Antes de eliminarlos o modificar su significado:

* localizar todas sus apariciones;
* determinar qué producción los genera;
* determinar qué parte del proceso los consume;
* comprobar si son necesarios para delimitar operaciones.

No eliminarlos simplemente porque parezcan innecesarios.

---

# 12. Archivo de salida

Si la generación de prefijo requiere un archivo de texto:

* conservar el formato solicitado;
* incluir número de línea;
* escribir una operación por línea;
* no agregar texto innecesario al archivo.

Ejemplo:

Linea 13: = #DX && < - + 35 * #XT 8 / ...

---

# 13. Manejo de errores

El sistema utiliza códigos de error propios.

No reemplazar el sistema de errores por excepciones genéricas si el proyecto ya tiene un mecanismo establecido.

Cuando se encuentre un error:

1. Identificar el token.
2. Identificar el ámbito si corresponde.
3. Identificar la línea.
4. Identificar la producción involucrada.
5. Mantener el código de error existente.

---

# 14. Excel

El proyecto puede utilizar Apache POI y archivos Excel para información como:

* TOKENS
* ERRORES
* CONTADORES
* CONTADORES_SINTAXIS

Antes de modificar la lectura/escritura de Excel:

* revisar la estructura de las hojas;
* conservar nombres de hojas;
* conservar columnas existentes;
* no modificar datos históricos sin necesidad.

---

# 15. Base de datos

La base de datos utilizada para símbolos puede ser:

Ambito_23130243

La tabla principal puede ser:

tabla_simbolos

Campos conocidos:

id
tipo
Clase
amb
Tarr
DimArr
NoPar
TParr

Si se modifica la conexión:

* revisar primero ConexionBD;
* conservar la inicialización existente;
* verificar que la base de datos y tabla existan;
* probar una consulta real.

No asumir que un problema de inserción significa que MySQL está mal configurado.

---

# 16. Procedimiento obligatorio antes de modificar

Para cualquier tarea importante:

### Paso 1

Leer CLAUDE.md.

### Paso 2

Identificar los archivos relacionados.

### Paso 3

Leer el código relevante.

### Paso 4

Explicar brevemente la causa del problema.

### Paso 5

Proponer el cambio mínimo.

### Paso 6

Realizar la modificación.

### Paso 7

Compilar.

### Paso 8

Ejecutar pruebas o una prueba representativa.

### Paso 9

Comparar la salida obtenida con la salida esperada.

### Paso 10

Informar exactamente qué archivos fueron modificados.

---

# 17. No asumir

Si falta información:

* buscar primero en el proyecto;
* revisar las definiciones existentes;
* revisar usos del método, clase o token;
* revisar producciones relacionadas.

No inventar cómo funciona una parte del compilador cuando puede comprobarse en el código.

---

# 18. Cuando el usuario proporcione una salida esperada

La salida proporcionada por el usuario debe tratarse como referencia.

No modificarla para hacer que coincida con el algoritmo.

Primero analizar qué transformación produce la salida esperada.

Si existe una discrepancia:

1. mostrar la salida actual;
2. mostrar la salida esperada;
3. localizar el primer punto donde divergen;
4. identificar la causa;
5. corregir el algoritmo.

---

# 19. Estilo de trabajo

El usuario prefiere:

* soluciones directas;
* cambios pequeños;
* código listo para utilizar;
* explicaciones claras;
* evitar teoría innecesaria;
* evitar refactorizaciones grandes;
* conservar el código existente siempre que sea posible.

Cuando entregue código modificado, indicar:

ARCHIVO: <nombre>

CAMBIO:
<qué se modificó>

RAZÓN:
<por qué>

PRUEBA:
<cómo comprobarlo>

RESULTADO: <resultado obtenido>
