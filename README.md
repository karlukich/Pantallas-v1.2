# Gregario Karoo · v1.9

Extensión para Hammerhead Karoo con ocho pantallas de datos a página completa, dos campos pequeños para la página del mapa y avisos de comer, beber y lluvia durante la salida.

| Campo en el Karoo | Qué muestra |
|---|---|
| **1 · Ruta** | Velocidad y media, potencia 3 s con zona y barra dentro de la zona, NORM, pulso con zona, pendiente coloreada, distancia y viento relativo a tu marcha |
| **2 · Velocidad y tiempo** | Velocidad, media y máxima, distancia total y de la vuelta, tiempo en movimiento y total, distancia a destino y hora de llegada |
| **3 · Potencia a fondo** | Potencia 3 s con zona y límites, W/kg, 10 s, 30 s, vuelta, NORM, media, máxima, IF, TSS y kJ |
| **4 · Pulso a fondo** | Pulso con zona y límites, % FC máx., media, máxima, vuelta, tiempo seguido en la zona, tiempo en Z4 o más, deriva y reparto del tiempo por zonas |
| **5 · Subida (Climber)** | Perfil del puerto actual coloreado por pendiente, puerto n de N, pendiente, VAM, lo que queda (km y m), tiempo a cima, potencia, W/kg y pulso |
| **6 · Entreno** | Entreno estructurado en curso: potencia en verde/azul/rojo según el objetivo del paso, barra con el rango, cuenta atrás del paso, cadencia frente a su objetivo, paso n de N, tiempo que queda, pulso, potencia de la vuelta y viento |
| **7 · Tiempo** | Viento relativo a tu marcha con flecha (de cara, lateral o a favor), km/h, rachas, efecto de cara o a favor, de dónde viene, temperatura, lluvia en la próxima hora y hora de la previsión |
| **8 · Ruta prevista** | Con una ruta cargada: miniatura coloreada por el viento que tendrás al pasar, km que quedan, hora de llegada y seis puntos de control con hora de paso, temperatura, lluvia y viento relativo |

**Capa de viento en el mapa:** con una ruta cargada, colorea cada tramo según te dará el viento previsto: rojo de cara, ámbar lateral y verde a favor. Se quita sola sin ruta o con viento de menos de 8 km/h, y se puede desactivar en ajustes.

**Campos pequeños** (para la página del mapa u otras con varios campos): **Viento** (flecha, km/h y de cara / a favor) y **Velocidad + distancia** (en un solo campo, lado a lado o uno sobre otro según la celda).

## Ajustes (se abren tocando la app en el Karoo)
| Grupo | Ajuste | Por defecto |
|---|---|---|
| Color | Color de las pantallas: automático / oscuro / claro | Automático |
| Color | Margen de luz: oscuro X min antes del atardecer y hasta X min después del amanecer | 20 min |
| Entreno | Potencia que se compara con el objetivo: 3, 5 o 10 s | 3 s |
| Entreno | Tolerancia fuera del objetivo antes de cambiar de color | 2 % |
| Entreno | Aviso de fin de paso (naranja) y pitido | 10 s, sí |
| Comer y beber | Recordatorio de comer, gramos por toma, máximo por hora, primer aviso | Sí, 25 g, 90 g/h, 30 min |
| Comer y beber | Recordatorio de beber e intervalo base | Sí, 20 min |
| Tiempo | Previsión del tiempo, viento en el mapa y aviso de lluvia (> 60 % en la próxima hora) | Sí, sí, sí |
| Bici | Tipo (carretera o MTB) y peso de bici y bolsas, para la pantalla 8 | Carretera, 10 kg |
| Pulso | Minutos antes de mostrar la deriva | 20 min |

Los cambios se aplican al momento.

## Cómo se calculan los avisos
- **Color automático:** oscuro si el Karoo está en modo noche; si no, según el sol donde estás (GPS) con el margen de luz. Sin posición guardada, de 8:00 a 18:30.
- **Comer:** el ritmo de hidratos sube con la intensidad (potencia media de 10 min frente al FTP del perfil): 30 g/h por debajo del 50 %, 60 g/h hacia el 75 % y 90 g/h desde el 90 %, con el máximo del ajuste. Avisa cada vez que completas una toma. El aviso muestra el ritmo, lo que llevas y una estimación de los hidratos gastados.
- **Beber:** intervalo base acortado con esfuerzo alto (hasta −33 %) y con calor desde 20 °C; nunca menos de 8 min.
- **Tiempo:** Open-Meteo (gratis, sin clave) para tu posición, cada 15 min o al moverte 5 km. La petición la hace el Karoo por wifi o por el móvil (app Companion). El viento es el previsto a 10 m de altura; a la altura del ciclista suele ser algo menor. El rumbo sale del GPS.
- **Ruta prevista:** tu velocidad en cada tramo de 200 m sale de un modelo físico: la potencia (la media de la salida tras 10 min o, al empezar, el 65 % del FTP), tu peso más el de la bici, la pendiente del perfil de la ruta, el rozamiento y el aire (distintos en carretera y MTB). En bajada se limita a 50 km/h en carretera y 35 en MTB. La previsión es de 8 puntos de la ruta a la hora en que pasarás.
- Los avisos salen solos mientras grabas, aunque no tengas ninguna pantalla de la app a la vista, y se pausan con la salida.

Notas:
- **Llegada** y **A destino** solo aparecen con una ruta cargada.
- **5 · Subida** necesita una ruta con subidas (el Climber del Karoo); sin puerto activo muestra «SIN PUERTO ACTIVO».
- **6 · Entreno** lee el entreno que esté ejecutando el Karoo (por ejemplo, el sincronizado desde intervals.icu); sin entreno muestra «SIN ENTRENO».
- Los typeId de las pantallas terminan en `-oscura` por compatibilidad: las páginas creadas con la v1.2 usando pantallas B siguen funcionando. Las que usaban pantallas C hay que volver a elegirlas.

Las pantallas se dibujan como una imagen (tipografía Barlow incluida en `app/src/main/assets/fonts`, licencia OFL) y se refrescan una vez por segundo.

## Compilar
GitHub Actions compila solo al subir los cambios (pestaña **Actions**); el APK queda en **Releases**.

## Instalar en el Karoo
Con la app Hammerhead Companion: en el móvil, mantener pulsado el enlace `gregario-karoo.apk` de la página de Releases → Compartir → Hammerhead Companion → **Install**.
El APK se firma siempre con la misma clave (`app/karoo.keystore`), así que se instala encima de la versión anterior.

## Ponerla en pantalla
Perfiles → página nueva de **un solo campo** → toca el campo → **Gregario** → elige la pantalla.

**Nombre:** antes se llamaba Karoo Pantallas. El paquete (`com.carlos.karoopantallas`), el id de la extensión (`pantallas`) y los typeId no cambian, para que se instale encima de la versión anterior y tus páginas sigan funcionando.

## Estructura
```
app/src/main/kotlin/com/carlos/karoopantallas/
├── PantallasExtension.kt     Registra las 7 pantallas y los 2 campos pequeños y arranca los procesos de fondo
├── PageScreen.kt             Recoge los datos y envía la imagen al Karoo cada segundo
├── MainActivity.kt           Pantalla de ajustes
├── render/                   Dibujo (Painter, Palette) y una clase por pantalla
└── data/
    ├── Settings.kt           Ajustes guardados en el Karoo
    ├── Theme.kt              Color automático (modo noche del Karoo y sol)
    ├── Coach.kt              Pitido de fin de paso y avisos de comer y beber (clase Fuel)
    ├── Weather.kt            Previsión de Open-Meteo, rumbo y viento relativo, aviso de lluvia
    ├── RoutePlan.kt          Modelo de velocidad, hora de paso, puntos de control y viento por tramo
    ├── RouteForecast.kt      Previsión de la ruta (Open-Meteo, varios puntos) para la pantalla 8
    ├── WindLayer.kt          Tramos de la ruta clasificados por el viento para la capa del mapa
    ├── RideTracker.kt        Zonas de pulso y deriva
    └── Snapshot.kt, Streams.kt, Polyline.kt
karoo-ext/                    SDK oficial de Hammerhead (v1.1.9, Apache 2.0)
```
