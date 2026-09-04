# Time Study Mobile — MVP 0.1

Aplicación Android para hacer estudios de tiempo directamente en el teléfono usando el mismo archivo Excel como plantilla.

## Ya implementado

- Importar `.xlsx` desde el teléfono.
- Detectar automáticamente la hoja del estudio y las columnas `t1` a `t5`.
- Leer los campos principales del archivo: posición, # operación, operación, máquina, código, antigüedad, tiempo FSN, tiempo en operación y operario.
- Cronómetro integrado con centésimas.
- `INICIAR`, `GRABAR` y `CANCELAR CICLO`.
- Al grabar, el tiempo se coloca en el siguiente `t1…t5` disponible.
- Permite borrar/repetir cualquier cronometraje.
- Navegación anterior/siguiente y progreso total.
- Agregar un operario debajo del actual conservando operación/máquina.
- Eliminar un operario del estudio.
- Editar datos del operario.
- Exportar partiendo del **mismo XLSX original**.
- Reescribe el bloque A:W de `Linea 11` y genera las fórmulas de detalle para las filas actuales.
- Las otras hojas del libro se mantienen porque el archivo original es la base de exportación.

## Archivo usado como referencia

El lector está diseñado contra el formato real analizado:

- Hoja: `Linea 11`
- Encabezado de detalle localizado dinámicamente por `t1` + `Operarios`.
- En el archivo de muestra el encabezado está en fila Excel 39 y los datos comienzan en fila 40.
- `t1..t5` corresponden a columnas J:N.

## Diseño

La UI está pensada para planta:

1. Importar estudio.
2. Ver un operario a la vez.
3. Cronómetro grande y botones grandes.
4. Ver inmediatamente t1-t5 y promedio.
5. Moverse al siguiente operario.
6. Agregar/eliminar operario cuando la distribución real no coincide con el Excel.
7. Finalizar y exportar el mismo documento.

## Importante para la siguiente iteración

El MVP ya tiene la estructura principal, pero antes de considerarlo producción deben cerrarse estas validaciones:

1. Probar exportación real en Android con el libro completo (imágenes/gráficos/objetos) y confirmar que POI Android conserva todos los elementos usados por este formato.
2. Ajustar las fórmulas resumen de filas 19:34 cuando se agreguen nuevas posiciones que no existían originalmente.
3. Autocompletar el operario desde `BD-OP` al escribir/escanear el código.
4. Guardado automático de sesión para recuperación si se cierra la app.
5. Confirmar oficialmente la fórmula TPF de 20% que debe mantenerse.
6. Validar y reparar las referencias antiguas de `Balanceo` si son necesarias para la exportación final.

## Abrir en Android Studio

Abrir la raíz del repositorio como proyecto Gradle.

Requisitos recomendados:

- Android Studio moderno
- JDK 17
- Android SDK 35
- Min Android 8.0 (API 26)
