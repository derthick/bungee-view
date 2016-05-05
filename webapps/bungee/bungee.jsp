<%
 String jnlpCodebase = request.getRequestURL().toString().replace("/bungee.jsp", "");
%>

<jnlp spec="1.0+" codebase="<%=jnlpCodebase%>" >
  <information>
    <title>Bungee View</title>
    <vendor>Carnegie-Mellon University</vendor>
    <homepage>href="Abo.html" </homepage>
    <description>Bungee View Image Collection Browser</description>
    <description kind="one-line">Bungee View Image Collection Browser</description>
    <description kind="tooltip">Bungee View</description>
    <description kind="short">Search, browse, and data-mine image collections based on their meta-data.</description>
    <icon href="Images/bungee-2-color.gif"/>
  </information>

  <%
   String query = request.getQueryString();
   if (query == null) query = "";
   boolean isAllPermissions = query.indexOf("informediaPort=")>=0 ||
                              query.indexOf("sessions=")>=1000;
   if (isAllPermissions) {
     /*
      * You can't specify individual Permissions! It's all or nothing.
      */
     out.write("  <security> <all-permissions/>  </security> ");
   }
  %>

  <resources>
    <%
     boolean isAssertions = query.indexOf("enableAssertions")>=0;
     out.write("<j2se version=\"1.7+\" initial-heap-size=\"256m\" max-heap-size=\"1024m\""
               + (isAssertions ? " java-vm-args=\"-enableassertions\"" : "") +"/>");
    
     if (isAllPermissions) {
       out.write(" <jar href=\"bungeeClientAllPermissions.jar\"/> ");
     } else {
       out.write(" <jar href=\"bungeeClient-signed.jar\"/> ");
     }
    %>


    <!-- <jar href="compile_unshrunk.jar"/> -->
    <!-- <jar href="javaExtensions.jar"/> -->
    <!-- <jar href="NLP-non-shrunk.jar"/> -->
    <!-- <jar href="PiccoloExtensions-non-shrunk.jar"/> -->
    <!-- <jar href="jboss-non-shrunk.jar"/> -->
    <!-- <jar href="commons-lang3-3.4.jar"/> -->


    <jar href="piccolo-signed.jar"/>
    <jar href="piccolox-signed.jar"/>
  </resources>

  <application-desc main-class="edu.cmu.cs.bungee.client.viz.bungeeCore.Bungee">
    <%
     if (query.length() > 0) query = query + "&";
     query = query + "servre=" + jnlpCodebase + "/Bungee";
     query=query.replaceAll("&", "!");
     out.write("<argument>" + query + "</argument>");
    
     response.setContentType("application/x-java-jnlp-file");
     response.setHeader("Content-Disposition", "inline; filename=bungee.jnlp");
    %>
  </application-desc>

</jnlp> 
