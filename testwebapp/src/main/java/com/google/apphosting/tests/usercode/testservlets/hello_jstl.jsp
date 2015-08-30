<%@ page contentType="text/html; charset=ASCII" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="start" value="${param.start}"/>
<c:set var="end" value="${param.end}"/>

<html>
  <head>
    <title>Hello World (JSTL)</title>
  </head>
  <body>
    <h1>Hello World!</h1>

    <c:forEach var="counter" begin="${start}" end="${end}">
      <h2><c:out value="Iteration ${counter}"/></h2>
    </c:forEach> 
  </body>
</html>
