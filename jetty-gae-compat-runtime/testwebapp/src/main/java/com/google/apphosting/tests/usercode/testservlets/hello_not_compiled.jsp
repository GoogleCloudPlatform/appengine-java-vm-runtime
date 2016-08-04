<%@ page contentType="text/html; charset=ASCII" language="java" session="false" %>

<%
  int start = Integer.valueOf(request.getParameter("start"));
  int end = Integer.valueOf(request.getParameter("end"));
%>

<html>
  <head>
    <title>Hello World (JSP)</title>
  </head>
  <body>
    <h1>Hello World!</h1>

    <%
      for (int i = start; i <= end; i++) {
    %>
        <h2>Iteration <%= i %></h2>
    <%
      }
    %>
  </body>
</html>
