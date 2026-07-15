# 🖥️ PluginMetricas: Monitor de Infraestructura para SailPoint IdentityIQ

Este repositorio contiene un Plugin Full-Stack personalizado para SailPoint IdentityIQ (IIQ). Sirve como una herramienta integral de monitorización del sistema en tiempo real y como **plantilla base (Boilerplate)** de referencia para arquitecturas avanzadas de plugins (Frontend, Backend REST y delegación en Reglas BeanShell).

---

## 🎯 ¿Qué hace? (Características Principales)

1. **Dashboard de Monitorización en Tiempo Real:** Proporciona una interfaz gráfica integrada en IdentityIQ para visualizar el estado de la JVM (Memoria RAM usada/libre/máxima), la carga de la CPU, los hilos de procesamiento (Threads), la versión del sistema y el número de nodos activos en el clúster.
2. **Auto-Refresco (Polling Dinámico):** Incluye un control (Toggle) que, al activarse, actualiza las métricas cada 500 milisegundos mediante peticiones asíncronas (`fetch`), sin necesidad de recargar la página.
3. **Inyección Global en la Interfaz (Snippet):** Añade de forma automática y no intrusiva un icono de acceso rápido (servidor) en la barra de navegación superior de IdentityIQ, visible desde cualquier pantalla de la plataforma.
4. **Notificaciones y Reportes por Correo:** Permite capturar una "foto" de las métricas mostradas en pantalla y enviarlas por correo electrónico a un administrador utilizando el motor SMTP nativo de SailPoint.

---

## 🏗️ ¿Cómo se ha creado? (Arquitectura y Conexión de Archivos)

El plugin está diseñado bajo una arquitectura de **3 capas** completamente desacopladas para maximizar la seguridad, el rendimiento y la facilidad de mantenimiento.

### 1. La Configuración (El Orquestador)

- **`manifest.xml`**: Es el mapa del plugin.
    - Declara la interfaz visual como una página completa (`FullPage`).
    - Expone el paquete de Java para que Tomcat/SailPoint habilite los endpoints REST (`restResources`).
    - Configura una expresión regular (`.*`) para inyectar el script del botón superior en todas las vistas de IIQ (`snippets`).

### 2. La Capa de Presentación (Frontend)

- **`ui/page.xhtml`**: Es la pantalla del Dashboard. Hereda la estructura visual de SailPoint mediante `<ui:composition>` vacío. Toda su lógica de interacción está escrita en JavaScript puro:
    - Llama al API Java para pedir datos y los inyecta en el DOM (`document.getElementById(...)`).
    - Captura el estado de las tarjetas y envía un JSON al servidor para disparar correos.
- **`ui/js/snippets/header.js`**: Utiliza jQuery para buscar la barra de navegación (`.navbar-right`) e incrustar dinámicamente un enlace (`<a>`) con el icono hacia la página del plugin.

### 3. La Capa de Control (Backend REST API)

- **`src/com/rporcel/metricas/MetricasResource.java`**: Es el cerebro del plugin, escrito en Java 17 y usando JAX-RS (Jersey).
    - Actúa como un puente entre la interfaz web y el servidor (Host).
    - Recopila datos físicos de la máquina (RAM, Procesadores, Uptime) mediante las librerías nativas de Java (`java.lang.management.*`).
    - Recibe órdenes desde el frontend, pero delega el procesamiento pesado (como enviar emails) al motor nativo de SailPoint.

### 4. La Capa de Lógica de Negocio (IdentityIQ Rule)

- **`PluginEnviarMetricasRule.xml`**: Es un script externo en **BeanShell**.
    - **La Magia de la Integración:** En lugar de enviar el correo directamente desde el código compilado en Java, el Java ejecuta `context.runRule()` inyectándole variables (`email` y `contenido`).
    - Esta regla se despierta, recibe los datos inyectados, construye un `EmailTemplate` y usa `context.sendEmailNotification(...)` para disparar el mensaje. Separar esto permite modificar textos, plantillas o destinatarios en caliente desde la consola de IIQ, sin recompilar el plugin.

---

## 🔗 ¿Qué URLs utiliza? (Endpoints)

El plugin interactúa con las siguientes rutas relativas dentro de tu entorno de SailPoint (asumiendo que el contexto es `/identityiq`):

### Interfaz de Usuario

- **URL del Dashboard:** `GET /identityiq/plugins/pluginPage.jsf?pn=PluginMetricas`

  *(Esta es la ruta a la que te lleva el icono de la barra superior).*

### Endpoints del API REST (Backend)

Las llamadas de JavaScript en `page.xhtml` apuntan a las siguientes URLs expuestas por `MetricasResource.java`:

- **URL para obtener métricas:**

  ```
  GET /identityiq/plugin/rest/metricas/stats
  ```

    - **Retorno:** Un objeto `JSON` con todos los datos del sistema operativo y SailPoint.

- **URL para enviar el correo:**

  ```
  POST /identityiq/plugin/rest/metricas/enviar
  ```

    - **Cuerpo esperado (JSON):**

      ```json
      {
        "contenido": "...",
        "email": "..."
      }
      ```

    - **Retorno:** Un objeto `JSON` con el estado:

      ```json
      {
        "status": "ok",
        "mensaje": "..."
      }
      ```

---

## ⚙️ ¿Cómo instalarlo TODO? (Guía Paso a Paso)

El proceso de despliegue se divide en dos fases: el empaquetado del plugin (ZIP) y la importación de la lógica de negocio (Regla).

### Paso 1: Compilar y Empaquetar el Plugin

El proyecto utiliza Apache Ant para gestionar la construcción.

1. Abre una terminal (o consola de comandos) y sitúate en el directorio raíz de este repositorio.
2. Ejecuta el comando de construcción:

   ```bash
   ant clean dist
   ```

3. Si la compilación finaliza correctamente, se generará el archivo:

   ```
   dist/PluginMetricas.zip
   ```

   Este es el paquete que se instalará en SailPoint IdentityIQ.

---

### Paso 2: Instalar el Plugin en SailPoint IdentityIQ

1. Inicia sesión en SailPoint IdentityIQ con un usuario con permisos de administrador (por ejemplo, `spadmin`).
2. En el menú superior derecho (**Engranaje / Tuerca**), selecciona **Plugins**.
3. Haz clic en **New**.
4. Selecciona y carga el archivo **`PluginMetricas.zip`** generado en el paso anterior.
5. Una vez finalizada la instalación, verifica que el plugin aparezca con el estado **Enabled**.

> **Nota:** Al habilitar el plugin, IdentityIQ leerá automáticamente el archivo `manifest.xml`, registrará los recursos REST y publicará la interfaz del plugin.

---

### Paso 3: Importar la Regla de Negocio (BeanShell Rule)

La regla de envío de correo es un artefacto nativo de SailPoint IdentityIQ y **no forma parte del archivo ZIP del plugin**, por lo que debe importarse de forma independiente.

1. En IdentityIQ, accede al menú **Engranaje / Tuerca → Global Settings**.
2. Haz clic en **Import from File**.
3. Selecciona el archivo **`PluginEnviarMetricasRule.xml`**, ubicado en la raíz de este repositorio.
4. Pulsa **Import**.
5. Si la operación se completa correctamente, IdentityIQ mostrará un mensaje de confirmación indicando que el objeto **Rule** ha sido creado en la base de datos.

> **Importante:** El endpoint REST encargado del envío de correos invoca esta regla mediante `context.runRule()`. Si la regla no está importada, la funcionalidad de envío de métricas por correo no estará disponible.

---

## ✅ Instalación completada

Tras completar los tres pasos anteriores tendrás disponible:

- ✅ El Dashboard de monitorización accesible desde el menú de plugins.
- ✅ El acceso rápido mediante el icono añadido a la barra superior de IdentityIQ.
- ✅ Los endpoints REST registrados y operativos.
- ✅ La regla BeanShell importada y preparada para enviar reportes por correo utilizando el motor SMTP nativo de SailPoint.