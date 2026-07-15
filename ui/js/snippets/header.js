// Esperamos a que la página de SailPoint termine de cargar
jQuery(document).ready(function () {

    // Construimos la URL dinámica hacia la página de tu plugin
    var pluginUrl = SailPoint.CONTEXT_PATH + '/plugins/pluginPage.jsf?pn=PluginMetricas';

    // Inyectamos el botón HTML en la lista de iconos de la barra superior derecha
    jQuery('ul.navbar-right li:first').before(
        '<li class="dropdown">' +
          ' <a href="' + pluginUrl + '" tabindex="0" role="menuitem" title="Monitor de Servidor">' +
          '   <i role="presentation" class="fa fa-server fa-lg" style="color: #00d2ff;"></i>' +
          ' </a>' +
        '</li>'
    );
});