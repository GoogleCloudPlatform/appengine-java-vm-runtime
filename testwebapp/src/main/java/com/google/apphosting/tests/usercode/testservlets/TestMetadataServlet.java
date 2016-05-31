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

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Tests metadata queries (__namespace__, __knd__, __property__)
 */
public class TestMetadataServlet extends HttpServletTest {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException {
    try {
      DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
      populate(ds, null);
      populate(ds, "ns1");
      populate(ds, "ns2");

      testMetadata(ds, response);
    } catch (Exception e) {
      throw new ServletException("Unexpected exception in TestMetadataServlet", e);
    }
  }

  public void testMetadata(DatastoreService ds, HttpServletResponse response) throws Exception {
    List<String> propsOfFun = propertiesOfKind(ds, "Fun");
    assertEquals("", 2, propsOfFun.size(), response);
    assertEquals("", "me", propsOfFun.get(0), response);
    assertEquals("", "you", propsOfFun.get(1), response);

    Object[] repsOfFunMe = representationsOf(ds, "Fun", "me").toArray();
    assertEquals("", 1, repsOfFunMe.length, response);
    assertEquals("", "STRING", repsOfFunMe[0], response);

    List<String> namespaces = getNamespaces(ds, null, null);
    assertEquals("", 3, namespaces.size(), response);
    assertEquals("", "", namespaces.get(0), response);
    assertEquals("", "ns1", namespaces.get(1), response);
    assertEquals("", "ns2", namespaces.get(2), response);

    Query query = new Query(Query.KIND_METADATA_KIND);
    query.addFilter(
        Entity.KEY_RESERVED_PROPERTY, Query.FilterOperator.LESS_THAN_OR_EQUAL, makeKindKey("M"));
    assertEquals("", "Fun", ds.prepare(query).asSingleEntity().getKey().getName(), response);
  }

  private void populate(DatastoreService ds, String namespace) {
    NamespaceManager.set(namespace);
    Entity entity = new Entity("Fun");
    entity.setProperty("me", "yes");
    entity.setProperty("you", 23);
    entity.setUnindexedProperty("haha", 0);
    ds.put(entity);
    entity = new Entity("Strange");
    ArrayList nowhereList = new ArrayList<Integer>();
    nowhereList.add(1);
    nowhereList.add(2);
    nowhereList.add(3);
    entity.setProperty("nowhere", nowhereList);
    ds.put(entity);
    Entity s2 = new Entity("Stranger");
    s2.setProperty("missing", new ArrayList<Integer>());
    ds.put(s2);
  }

  Key makePropertyKey(String kind, String property) {
    return KeyFactory.createKey(makeKindKey(kind), Query.PROPERTY_METADATA_KIND, property);
  }

  Key makeKindKey(String kind) {
    return KeyFactory.createKey(Query.KIND_METADATA_KIND, kind);
  }

  Key makeNamespaceKey(String name) {
    return KeyFactory.createKey(Query.NAMESPACE_METADATA_KIND, name);
  }

  List<String> propertiesOfKind(DatastoreService ds, String kind) {
    Query query = new Query(Query.PROPERTY_METADATA_KIND);
    query.setAncestor(makeKindKey(kind));
    ArrayList<String> results = new ArrayList<String>();
    for (Entity e : ds.prepare(query).asIterable()) {
      results.add(e.getKey().getName());
    }
    return results;
  }

  Collection<String> representationsOf(DatastoreService ds, String kind, String property) {
    Query query = new Query(Query.PROPERTY_METADATA_KIND);
    query.setAncestor(makePropertyKey(kind, property));
    Entity propInfo = ds.prepare(query).asSingleEntity();
    return (Collection<String>) propInfo.getProperty("property_representation");
  }

  List<String> getNamespaces(DatastoreService ds, String startNamespace, String endNamespace) {
    Query query = new Query(Query.NAMESPACE_METADATA_KIND);
    if (startNamespace != null) {
      query.addFilter(
          Entity.KEY_RESERVED_PROPERTY,
          Query.FilterOperator.GREATER_THAN_OR_EQUAL,
          makeNamespaceKey(startNamespace));
    }
    if (endNamespace != null) {
      query.addFilter(
          Entity.KEY_RESERVED_PROPERTY,
          Query.FilterOperator.LESS_THAN_OR_EQUAL,
          makeNamespaceKey(endNamespace));
    }
    ArrayList<String> results = new ArrayList<>();
    for (Entity e : ds.prepare(query).asIterable()) {
      if (e.getKey().getId() != 0) {
        results.add("");
      } else {
        results.add(e.getKey().getName());
      }
    }
    return results;
  }
}
