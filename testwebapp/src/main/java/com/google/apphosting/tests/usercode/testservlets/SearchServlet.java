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

import com.google.appengine.api.search.Document;
import com.google.appengine.api.search.Field;
import com.google.appengine.api.search.Index;
import com.google.appengine.api.search.IndexSpec;
import com.google.appengine.api.search.SearchService;
import com.google.appengine.api.search.SearchServiceFactory;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * See {@link com.google.appengine.tools.development.DevAppServerAgainstSearchBlackBoxTest}
 * for information on how we use this servlet in our tests.
 *
 */
public class SearchServlet extends HttpServlet {
  @Override
  protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    initIndexes();
  }

  public void initIndexes() {
    SearchService search = SearchServiceFactory.getSearchService();
    IndexSpec.Builder spec = IndexSpec.newBuilder();

    for (int i = 0; i < 25; i++) {
      String name = String.format("index%s", i);
      spec.setName(name);
      addDocuments(search.getIndex(spec), name, i == 0 ? 25 : 1);
    }
    search = SearchServiceFactory.getSearchService("ns");
    spec.setName("<b>");
    addDocuments(search.getIndex(spec), "other", 1);
  }

  public void addDocuments(Index index, String name, int numDocs) {
    for (int i = 0; i < numDocs; i++) {
      addDocument(index, String.format("%s_doc%d", name, i));
    }
  }

  public void addDocument(Index index, String docid) {
    Document.Builder builder = Document.newBuilder();
    builder.setId(docid);
    builder.setRank(docid.hashCode());

    Field.Builder field = Field.newBuilder();
    field.setName("title");
    field.setText(String.format("Title: title%%<%s>", docid));
    builder.addField(field);

    field = Field.newBuilder();
    field.setName("body");
    field.setHTML(String.format("<h3>body of %s, some string</h3>", docid));
    builder.addField(field);

    field = Field.newBuilder();
    field.setName("atom");
    field.setAtom(String.format("atom%% <%s>", docid));
    builder.addField(field);

    field = Field.newBuilder();
    field.setName("number");
    field.setNumber(docid.hashCode() % 4096);
    builder.addField(field);

    field = Field.newBuilder();
    field.setName("date");
    field.setDate(new Date(2011 - 1900, 11 - 1, (docid.hashCode() % 30) + 1));
    builder.addField(field);

    index.put(builder.build());
  }
}
