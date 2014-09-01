/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.olingo2;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchChangeRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchQueryRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchResponse;
import org.apache.camel.component.olingo2.api.batch.Operation;
import org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl;
import org.apache.camel.component.olingo2.internal.Olingo2Constants;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.ep.feed.ODataFeed;
import org.apache.olingo.odata2.api.servicedocument.ServiceDocument;
import org.apache.olingo.odata2.core.uri.SystemQueryOption;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.olingo2.api.Olingo2App} APIs.
 * The integration test runs against Apache Olingo 2.0 sample server
 * described at http://olingo.apache.org/doc/sample-setup.html
 */
public class Olingo2AppIntegrationTest extends AbstractOlingo2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Olingo2AppIntegrationTest.class);
    private static final String ID_PROPERTY = "Id";
    private static final String MANUFACTURERS = "Manufacturers";
    private static final String TEST_MANUFACTURER = "Manufacturers('1')";
    private static final String CARS = "Cars";
    private static final String TEST_RESOURCE_CONTENT_ID = "1";
    private static final String ADDRESS = "Address";
    private static final String TEST_RESOURCE = "$1";
    private static final String TEST_CREATE_MANUFACTURER = "Manufacturers('123')";

    @Test
    public void testRead() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();

        // read ServiceDocument
        final ServiceDocument document = requestBodyAndHeaders("direct://READSERVICEDOC", null, headers);
        assertNotNull(document);
        assertFalse("ServiceDocument entity sets", document.getEntitySetsInfo().isEmpty());

        // parameter type is java.util.Map
        final HashMap<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(SystemQueryOption.$top.name(), "5");
        headers.put("CamelOlingo2.queryParams", queryParams);

        // read ODataFeed
        final ODataFeed manufacturers = requestBodyAndHeaders("direct://READFEED", null, headers);
        assertNotNull(manufacturers);
        assertEquals("Manufacturers feed size", 5, manufacturers.getEntries().size());

        // read ODataEntry
        headers.clear();
        headers.put(Olingo2Constants.PROPERTY_PREFIX + "keyPredicate", "'1'");
        final ODataEntry manufacturer = requestBodyAndHeaders("direct://READENTRY", null, headers);
        assertNotNull(manufacturer);
        assertEquals("Manufacturer Id", "1", manufacturer.getProperties().get(ID_PROPERTY));
    }

    @Test
    public void testCreateUpdateDelete() throws Exception {
        final Map<String, Object> data = getEntityData();
        Map<String, Object> address;

        final ODataEntry manufacturer = requestBody("direct://CREATE", data);
        assertNotNull("Created Manufacturer", manufacturer);
        assertEquals("Created Manufacturer Id", "123", manufacturer.getProperties().get(ID_PROPERTY));

        // update
        data.put("Name", "MyCarManufacturer Renamed");
        address = (Map<String, Object>)data.get("Address");
        address.put("Street", "Main Street");

        HttpStatusCodes status = requestBody("direct://UPDATE", data);
        assertNotNull("Update status", status);
        assertEquals("Update status", HttpStatusCodes.NO_CONTENT.getStatusCode(), status.getStatusCode());

        // delete
        status = requestBody("direct://DELETE", null);
        assertNotNull("Delete status", status);
        assertEquals("Delete status", HttpStatusCodes.NO_CONTENT.getStatusCode(), status.getStatusCode());
    }

    private Map<String, Object> getEntityData() {
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("Id", "123");
        data.put("Name", "MyCarManufacturer");
        data.put("Founded", new Date());
        Map<String, Object> address = new HashMap<String, Object>();
        address.put("Street", "Main");
        address.put("ZipCode", "42421");
        address.put("City", "Fairy City");
        address.put("Country", "FarFarAway");
        data.put("Address", address);
        return data;
    }

    @Test
    public void testBatch() throws Exception {
        final List<Olingo2BatchRequest> batchParts = new ArrayList<Olingo2BatchRequest>();

        // 1. Edm query
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(Olingo2AppImpl.METADATA).build());

        // 2. feed query
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(MANUFACTURERS).build());

        // 3. read
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_MANUFACTURER).build());

        // 4. read with expand
        final HashMap<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(SystemQueryOption.$expand.toString(), CARS);
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_MANUFACTURER).queryParams(queryParams).build());

        // 5. create
        final Map<String, Object> data = getEntityData();
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(MANUFACTURERS).
            contentId(TEST_RESOURCE_CONTENT_ID).operation(Operation.CREATE).body(data).build());

        // 6. update
        final Map<String, Object> updateData = new HashMap<String, Object>(data);
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) updateData.get(ADDRESS);
        updateData.put("Name", "MyCarManufacturer Renamed");
        address.put("Street", "Main Street");

        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE).operation(Operation.UPDATE)
            .body(updateData).build());

        // 7. delete
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE).operation(Operation.DELETE).build());

        // 8. read to verify delete
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_CREATE_MANUFACTURER).build());

        // execute batch request
        final List<Olingo2BatchResponse> responseParts = requestBody("direct://BATCH", batchParts);
        assertNotNull("Batch response", responseParts);
        assertEquals("Batch responses expected", 8, responseParts.size());

        final Edm edm = (Edm) responseParts.get(0).getBody();
        assertNotNull(edm);

        final ODataFeed feed = (ODataFeed) responseParts.get(1).getBody();
        assertNotNull(feed);

        ODataEntry dataEntry = (ODataEntry) responseParts.get(2).getBody();
        assertNotNull(dataEntry);

        dataEntry = (ODataEntry) responseParts.get(3).getBody();
        assertNotNull(dataEntry);

        dataEntry = (ODataEntry) responseParts.get(4).getBody();
        assertNotNull(dataEntry);

        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), responseParts.get(5).getStatusCode());
        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), responseParts.get(6).getStatusCode());

        assertEquals(HttpStatusCodes.NOT_FOUND.getStatusCode(), responseParts.get(7).getStatusCode());
        final Exception exception = (Exception) responseParts.get(7).getBody();
        assertNotNull(exception);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test routes for read
                from("direct://READSERVICEDOC")
                    .to("olingo2://read/");

                from("direct://READFEED")
                    .to("olingo2://read/Manufacturers?$orderBy=Name%20asc");

                from("direct://READENTRY")
                    .to("olingo2://read/Manufacturers");

                // test route for create
                from("direct://CREATE")
                  .to("olingo2://create/Manufacturers");

                // test route for update
                from("direct://UPDATE")
                  .to("olingo2://update/Manufacturers('123')");

                // test route for delete
                from("direct://DELETE")
                  .to("olingo2://delete/Manufacturers('123')");

/*
                // test route for merge
                from("direct://MERGE")
                  .to("olingo2://merge");

                // test route for patch
                from("direct://PATCH")
                  .to("olingo2://patch");
*/

                // test route for batch
                from("direct://BATCH")
                    .to("olingo2://batch");

            }
        };
    }
}
