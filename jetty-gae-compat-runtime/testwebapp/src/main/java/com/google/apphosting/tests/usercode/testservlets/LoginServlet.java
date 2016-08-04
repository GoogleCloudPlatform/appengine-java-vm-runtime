/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.apphosting.tests.usercode.testservlets;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginServlet extends HttpServlet {
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    res.setContentType("text/html");
    res.getWriter().println("<html>");
    res.getWriter().println("<head>");
    res.getWriter().println("<title>whoami</title>");
    res.getWriter().println("</head>");
    res.getWriter().println("<body>");

    UserService userService = UserServiceFactory.getUserService();

    if (userService.isUserLoggedIn()) {
      User user = userService.getCurrentUser();

      res.getWriter().println("<h1>You are " + user.getNickname() + ".</h1>");

      if (userService.isUserAdmin()) {
        res.getWriter().println("<h2>You are an admin! :)</h2>");
      } else {
        res.getWriter().println("<h2>You are not an admin... :(</h2>");
      }

      res.getWriter().println("<h1>Your user ID is " + user.getUserId() + ".</h1>");
    } else {
      res.getWriter().println("<h1>You are not logged in.</h1>");
    }

    String destURL = "/whoami";
    String loginURL = userService.createLoginURL(destURL);
    String logoutURL = userService.createLogoutURL(destURL);

    res.getWriter().println("<br>");
    res.getWriter().println("<a href=\"" + loginURL + "\">login</a>");
    res.getWriter().println("<br>");
    res.getWriter().println("<a href=\"" + logoutURL + "\">logout</a>");
    res.getWriter().println("</body>");
    res.getWriter().println("</html>");
  }
}
