# Karoo Pantallas · v1.2

Extensión para Hammerhead Karoo con cinco pantallas de datos a página completa, cada una en modo oscuro (B) y claro (C):

| Campo en el Karoo | Qué muestra |
|---|---|
| **1B / 1C · Ruta** | Velocidad y media, potencia 3 s con zona y barra dentro de la zona, NORM, pulso con zona, pendiente coloreada, distancia y desnivel |
| **2B / 2C · Velocidad y tiempo** | Velocidad, media y máxima, distancia total y de la vuelta, tiempo en movimiento y total, distancia a destino y hora de llegada |
| **3B / 3C · Potencia a fondo** | Potencia 3 s con zona y límites, W/kg, 10 s, 30 s, vuelta, NORM, media, máxima, IF, TSS y kJ |
| **4B / 4C · Pulso a fondo** | Pulso con zona y límites, % FC máx., media, máxima, vuelta, tiempo seguido en la zona, tiempo en Z4 o más, deriva y reparto del tiempo por zonas |
| **5B / 5C · Subida (Climber)** | Perfil del puerto actual coloreado por pendiente, puerto n de N, pendiente, VAM, lo que queda (km y m), tiempo a cima, potencia, W/kg y pulso |

Las zonas, el peso, la FC máxima y las unidades salen del perfil del Karoo.

Notas:
- **Llegada** y **A destino** solo aparecen con una ruta cargada.
- **5B / 5C** necesita una ruta con subidas (el Climber del Karoo); sin puerto activo muestra «SIN PUERTO ACTIVO» y el resto de datos.
- **Tiempo en zonas, En zona, Z4 + y Deriva** los calcula la app mientras grabas. Se ponen a cero al empezar una salida nueva. La deriva (pulso frente a potencia, primera mitad frente a la segunda) aparece a partir de 20 minutos.

Las pantallas se dibujan como una imagen (tipografía Barlow incluida en `app/src/main/assets/fonts`, licencia OFL) para que se vean igual que las maquetas, y se refrescan una vez por segundo.

## Compilar
GitHub Actions compila solo al subir los cambios (pestaña **Actions**); el APK queda en **Releases**.

## Instalar en el Karoo
Con la app Hammerhead Companion: en el móvil, mantener pulsado el enlace `karoo-pantallas.apk` de la página de Releases → Compartir → Hammerhead Companion → **Install**.
El APK se firma siempre con la misma clave (`app/karoo.keystore`), así que se instala encima de la versión anterior.

## Ponerla en pantalla
Perfiles → página nueva de **un solo campo** → toca el campo → **Pantallas** → elige la pantalla.

## Estructura
```
app/src/main/kotlin/com/carlos/karoopantallas/
├── PantallasExtension.kt     Registra las 10 pantallas en el Karoo
├── PageScreen.kt             Recoge los datos y envía la imagen al Karoo cada segundo
├── render/Painter.kt         Dibujo en coordenadas de diseño (480 × 620)
├── render/Palette.kt         Colores del modo oscuro y del claro
├── render/RutaLayout.kt      1B / 1C
├── render/VelocidadLayout.kt 2B / 2C
├── render/PotenciaLayout.kt  3B / 3C
├── render/PulsoLayout.kt     4B / 4C
├── render/ClimberLayout.kt   5B / 5C
└── data/                     Datos, unidades, formatos y RideTracker (zonas y deriva)
karoo-ext/                    SDK oficial de Hammerhead (v1.1.9, Apache 2.0)
```
