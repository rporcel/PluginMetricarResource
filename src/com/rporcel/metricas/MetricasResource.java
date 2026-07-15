package com.rporcel.metricas;

import sailpoint.api.SailPointContext;
import sailpoint.object.Server;
import sailpoint.object.Rule; // IMPORTANTE: Importamos el objeto Rule de SailPoint
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.AllowAll;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ==============================================================================
 * CLASE PRINCIPAL DEL API REST DEL PLUGIN
 * Esta clase actúa como controlador (Backend) de tu Plugin.
 * Todas las peticiones que el navegador hace a /plugin/rest/metricas/... entran aquí.
 * ==============================================================================
 */
@Path("metricas") // Define la ruta base del plugin en la URL REST
@AllowAll         // Permite a cualquier usuario logueado usar esta API
public class MetricasResource extends BasePluginResource {

    @Override
    public String getPluginName() {
        // Obligatorio: debe coincidir exactamente con el nombre en manifest.xml
        return "PluginMetricas";
    }

    /**
     * --------------------------------------------------------------------------
     * ENDPOINT GET: /stats
     * Propósito: Recolectar datos del sistema y devolverlos al frontend en JSON.
     * --------------------------------------------------------------------------
     */
    @GET
    @Path("stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getStats() throws Exception {
        SailPointContext context = getContext();
        Map<String, Object> stats = new HashMap<>();

        // --- MÉTRICAS DE RAM ---
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory() / (1024 * 1024);
        long freeMem = runtime.freeMemory() / (1024 * 1024);

        stats.put("memoria_usada_mb", totalMem - freeMem);
        stats.put("memoria_libre_mb", freeMem);
        stats.put("memoria_maxima_mb", runtime.maxMemory() / (1024 * 1024));

        // --- MÉTRICAS DE CPU Y SO ---
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        stats.put("sistema_operativo", osBean.getName() + " (" + osBean.getVersion() + ")");
        stats.put("arquitectura_so", osBean.getArch());
        stats.put("nucleos_procesador", osBean.getAvailableProcessors());

        double load = osBean.getSystemLoadAverage();
        stats.put("carga_sistema", load < 0 ? "N/D" : String.format("%.2f", load));

        // --- MÉTRICAS DE HILOS Y UPTIME ---
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long uptimeMillis = rb.getUptime();
        long uptimeHours = TimeUnit.MILLISECONDS.toHours(uptimeMillis);
        long uptimeMinutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) - TimeUnit.HOURS.toMinutes(uptimeHours);

        stats.put("tiempo_actividad", uptimeHours + "h " + uptimeMinutes + "m");
        stats.put("version_java", rb.getVmName() + " " + rb.getSpecVersion());

        ThreadMXBean tb = ManagementFactory.getThreadMXBean();
        stats.put("hilos_activos", tb.getThreadCount());
        stats.put("hilos_pico", tb.getPeakThreadCount());

        // --- MÉTRICAS DE NATIVE SAILPOINT ---
        int nodos = context.countObjects(Server.class, null);
        stats.put("nodos_iiq", nodos);

        return stats; // SailPoint (gracias a Jersey) convierte este Map a JSON automáticamente.
    }

    /**
     * --------------------------------------------------------------------------
     * ENDPOINT POST: /enviar
     * Propósito: Recibe datos del frontend y ejecuta una RULE de SailPoint.
     * Ejemplo perfecto de delegación de lógica.
     * --------------------------------------------------------------------------
     */
    @POST
    @Path("enviar")
    @Consumes(MediaType.APPLICATION_JSON) // Esperamos recibir JSON desde el JS (fetch)
    @Produces(MediaType.APPLICATION_JSON) // Devolvemos JSON como respuesta
    public Map<String, String> dispararRegla(Map<String, String> payload) throws Exception {

        // 1. Obtenemos el contexto (conexión a la BBDD de SailPoint)
        SailPointContext context = getContext();
        Map<String, String> respuesta = new HashMap<>();

        try {
            // 2. Extraemos los parámetros que nos envía el JavaScript
            String metricasText = payload.get("contenido");
            String destinatario = payload.get("email");

            // 3. Preparamos los parámetros que vamos a inyectar en la Rule
            // Estos parámetros viajarán al Beanshell y se podrán leer con params.get("...")
            Map<String, Object> ruleParams = new HashMap<>();
            ruleParams.put("contenido", metricasText);
            ruleParams.put("email", destinatario);

            // 4. Buscamos la regla por su nombre exacto en la base de datos de IdentityIQ
            Rule enviarRule = context.getObjectByName(Rule.class, "PluginEnviarMetricasRule");

            // Validamos que el administrador no haya olvidado importar el XML de la Regla
            if (enviarRule == null) {
                throw new Exception("No se encontró la regla 'PluginEnviarMetricasRule' en el sistema.");
            }

            // 5. ¡LA MAGIA OCURRE AQUÍ! Ejecutamos la regla pasándole el contexto y los parámetros.
            // Esto dispara el Beanshell que creamos en el Paso 1.
            Object ruleResult = context.runRule(enviarRule, ruleParams);

            // 6. Preparamos la respuesta de éxito para el Frontend
            respuesta.put("status", "ok");
            respuesta.put("mensaje", "Regla ejecutada. Resultado: " + (String) ruleResult);

        } catch (Exception e) {
            // 7. Manejo de errores: Si algo falla, el frontend lo sabrá
            respuesta.put("status", "error");
            respuesta.put("mensaje", "Fallo al ejecutar la regla: " + e.getMessage());
        }

        return respuesta;
    }
}