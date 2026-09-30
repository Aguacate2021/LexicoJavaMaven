# Compiler Debugging

## Objetivo

Encontrar la causa real de errores en AnalyzerIDE sin realizar cambios innecesarios.

## Procedimiento

1. Reproducir el error.
2. Registrar el mensaje.
3. Identificar archivo.
4. Identificar método.
5. Identificar línea.
6. Seguir el flujo de datos.
7. Encontrar dónde aparece la primera inconsistencia.
8. Corregir únicamente esa parte.
9. Compilar nuevamente.
10. Reproducir la prueba.

## Regla importante

No asumir que el lugar donde aparece el error es necesariamente el origen del error.

Seguir el flujo hacia atrás hasta encontrar dónde se produjo el dato incorrecto.

## Comparación

Cuando exista una salida esperada:

* comparar token por token;
* encontrar la primera diferencia;
* rastrear el origen de ese token;
* corregir el proceso que produjo la diferencia.

## Cambios

Evitar:

* refactorizaciones;
* cambios de arquitectura;
* nuevas dependencias;
* reemplazo completo de clases;
* cambios de nombres.

Preferir:

* modificaciones localizadas;
* reutilización del código existente;
* pruebas pequeñas.

## Resultado

Al terminar informar:

Archivo:
Método:
Problema:
Causa:
Cambio:
Prueba:
Resultado:
