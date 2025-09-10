#!/bin/bash

# Nombre de la carpeta destino
DESTINO="miproyecto_copy"

# Obtener el nombre del script actual
NOMBRE_SCRIPT=$(basename "$0")

# Obtener la ubicación actual
UBICACION_ACTUAL=$(pwd)

echo "Iniciando copia de archivos..."
echo "Ubicación actual: $UBICACION_ACTUAL"
echo "Carpeta destino: $DESTINO"
echo "Excluyendo script: $NOMBRE_SCRIPT"

# Crear la carpeta destino si no existe
if [ ! -d "$DESTINO" ]; then
    echo "Creando carpeta '$DESTINO'..."
    mkdir "$DESTINO"
else
    echo "La carpeta '$DESTINO' ya existe."
fi

echo "Copiando archivos (excluyendo la carpeta '$DESTINO' y el script '$NOMBRE_SCRIPT')..."

# Copiar todos los archivos directamente a la carpeta destino, sin estructura de subdirectorios
# Usamos find con -path para excluir la carpeta destino y -name para excluir el script
find . -path "./$DESTINO" -prune -o -name "$NOMBRE_SCRIPT" -prune -o -type f -print0 | while IFS= read -r -d '' archivo; do
    # Obtener solo el nombre del archivo (sin la ruta)
    nombre_archivo=$(basename "$archivo")
    
    # Si ya existe un archivo con el mismo nombre, agregar un sufijo único
    contador=1
    archivo_destino="$nombre_archivo"
    while [ -f "$DESTINO/$archivo_destino" ]; do
        # Separar nombre y extensión
        nombre_base="${nombre_archivo%.*}"
        extension="${nombre_archivo##*.}"
        
        if [ "$extension" = "$nombre_archivo" ]; then
            # No tiene extensión
            archivo_destino="${nombre_base}_$contador"
        else
            # Tiene extensión
            archivo_destino="${nombre_base}_$contador.$extension"
        fi
        contador=$((contador + 1))
    done
    
    # Copiar el archivo directamente a la carpeta destino
    cp "$archivo" "$DESTINO/$archivo_destino"
    echo "Copiado: $archivo -> $DESTINO/$archivo_destino"
done

echo "-------------------"
echo "¡Copia completada!"
echo "Todos los archivos han sido copiados directamente a la carpeta '$DESTINO' (sin subcarpetas)"
echo "-------------------"
echo "Se excluyeron: la carpeta '$DESTINO' y el script '$NOMBRE_SCRIPT'"
