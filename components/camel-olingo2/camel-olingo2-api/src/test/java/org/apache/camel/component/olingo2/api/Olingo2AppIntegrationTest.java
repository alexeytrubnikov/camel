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
package org.apache.camel.component.olingo2.api;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchChangeRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchQueryRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchRequest;
import org.apache.camel.component.olingo2.api.batch.Olingo2BatchResponse;
import org.apache.camel.component.olingo2.api.batch.Operation;
import org.apache.camel.component.olingo2.api.impl.AbstractFutureCallback;
import org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.edm.EdmEntitySetInfo;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.ep.feed.ODataDeltaFeed;
import org.apache.olingo.odata2.api.ep.feed.ODataFeed;
import org.apache.olingo.odata2.api.exception.ODataApplicationException;
import org.apache.olingo.odata2.api.servicedocument.Collection;
import org.apache.olingo.odata2.api.servicedocument.ServiceDocument;
import org.apache.olingo.odata2.core.commons.ContentType;
import org.apache.olingo.odata2.core.uri.SystemQueryOption;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for {@link org.apache.camel.component.olingo2.api.impl.Olingo2AppImpl}.
 * To test run the sample Olingo2 Server as outlined at
 * http://olingo.apache.org/doc/tutorials/Olingo2V2BasicClientSample.html
 */
public class Olingo2AppIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(Olingo2AppIntegrationTest.class);
    private static final long TIMEOUT = 10;

    private static final String MANUFACTURERS = "Manufacturers";
    private static final String ADDRESS = "Address";
    private static final String CARS = "Cars";

    private static final String TEST_KEY = "'1'";
    private static final String TEST_CREATE_KEY = "'123'";
    private static final String TEST_MANUFACTURER = MANUFACTURERS + "(" + TEST_KEY + ")";
    private static final String TEST_CREATE_MANUFACTURER = MANUFACTURERS + "(" + TEST_CREATE_KEY + ")";

    private static final String TEST_RESOURCE_CONTENT_ID = "1";
    private static final String TEST_RESOURCE = "$" + TEST_RESOURCE_CONTENT_ID;

    private static final char NEW_LINE = '\n';
    private static final String TEST_CAR = "Manufacturers('1')/Cars('1')";
    private static final String TEST_MANUFACTURER_FOUNDED_PROPERTY = "Manufacturers('1')/Founded";
    private static final String TEST_MANUFACTURER_FOUNDED_VALUE = "Manufacturers('1')/Founded/$value";
    private static final String FOUNDED_PROPERTY = "Founded";
    private static final String TEST_MANUFACTURER_ADDRESS_PROPERTY = "Manufacturers('1')/Address";
    private static final String TEST_MANUFACTURER_LINKS_CARS = "Manufacturers('1')/$links/Cars";
    private static final String TEST_CAR_LINK_MANUFACTURER = "Cars('1')/$links/Manufacturer";
    private static final String COUNT_OPTION = "/$count";

    private static String TEST_SERVICE_URL = "http://localhost:8080/MyFormula.svc";
    //    private static String TEST_SERVICE_URL = "http://localhost:8080/cars-annotations-sample/MyFormula.svc";
//    private static ContentType TEST_FORMAT = ContentType.APPLICATION_XML_CS_UTF_8;
    private static ContentType TEST_FORMAT = ContentType.APPLICATION_JSON_CS_UTF_8;
    private static final String INDEX = "/index.jsp";

    private static Olingo2App olingoApp;
    private static final String GEN_SAMPLE_DATA = "genSampleData=true";
    private static Edm edm;

    @BeforeClass
    public static void beforeClass() throws Exception {

        olingoApp = new Olingo2AppImpl(TEST_SERVICE_URL);
        olingoApp.setContentType(TEST_FORMAT.toString());

        LOG.info("Generate sample data ");
        generateSampleData(TEST_SERVICE_URL);

        LOG.info("Read Edm ");
        final TestOlingo2ResponseHandler<Edm> responseHandler = new TestOlingo2ResponseHandler<Edm>();

        olingoApp.read(null, Olingo2AppImpl.METADATA, null, responseHandler);

        edm = responseHandler.await();
        LOG.info("Read default EntityContainer:  {}", responseHandler.await().getDefaultEntityContainer().getName());
    }

    @AfterClass
    public static void afterClass() {
        olingoApp.close();
    }

    @Test
    public void testServiceDocument() throws Exception {
        final TestOlingo2ResponseHandler<ServiceDocument> responseHandler =
            new TestOlingo2ResponseHandler<ServiceDocument>();

        olingoApp.read(null, "", null, responseHandler);

        final ServiceDocument serviceDocument = responseHandler.await();
        final List<Collection> collections = serviceDocument.getAtomInfo().getWorkspaces().get(0).getCollections();
        assertEquals("Service Atom Collections", 3, collections.size());
        LOG.info("Service Atom Collections:  {}", collections);

        final List<EdmEntitySetInfo> entitySetsInfo = serviceDocument.getEntitySetsInfo();
        assertEquals("Service Entity Sets", 3, entitySetsInfo.size());
        LOG.info("Service Document Entries:  {}", entitySetsInfo);
    }

    @Test
    public void testReadFeed() throws Exception {
        final TestOlingo2ResponseHandler<ODataFeed> responseHandler = new TestOlingo2ResponseHandler<ODataFeed>();

        olingoApp.read(edm, MANUFACTURERS, null, responseHandler);

        final ODataFeed dataFeed = responseHandler.await();
        assertNotNull("Data feed", dataFeed);
        LOG.info("Entries:  {}", prettyPrint(dataFeed));
    }

    @Test
    public void testReadEntry() throws Exception {
        final TestOlingo2ResponseHandler<ODataEntry> responseHandler = new TestOlingo2ResponseHandler<ODataEntry>();

        olingoApp.read(edm, TEST_MANUFACTURER, null, responseHandler);
        ODataEntry entry = responseHandler.await();
        LOG.info("Single Entry:  {}", prettyPrint(entry));

        responseHandler.reset();

        olingoApp.read(edm, TEST_CAR, null, responseHandler);
        entry = responseHandler.await();
        LOG.info("Single Entry:  {}", prettyPrint(entry));

        responseHandler.reset();
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(SystemQueryOption.$expand.toString(), CARS);

        olingoApp.read(edm, TEST_MANUFACTURER, queryParams, responseHandler);

        ODataEntry entryExpanded = responseHandler.await();
        LOG.info("Single Entry with expanded Cars relation:  {}", prettyPrint(entryExpanded));
    }

    @Test
    public void testReadUpdateProperties() throws Exception {
        // test simple property Manufacturer.Founded
        final TestOlingo2ResponseHandler<Map<String, Object>> propertyHandler =
            new TestOlingo2ResponseHandler<Map<String, Object>>();

        olingoApp.read(edm, TEST_MANUFACTURER_FOUNDED_PROPERTY, null, propertyHandler);

        Calendar founded = (Calendar) propertyHandler.await().get(FOUNDED_PROPERTY);
        LOG.info("Founded property {}", founded.toString());

        final TestOlingo2ResponseHandler<Calendar> valueHandler = new TestOlingo2ResponseHandler<Calendar>();

        olingoApp.read(edm, TEST_MANUFACTURER_FOUNDED_VALUE, null, valueHandler);

        founded = valueHandler.await();
        LOG.info("Founded property {}", founded.toString());

        final TestOlingo2ResponseHandler<HttpStatusCodes> statusHandler =
            new TestOlingo2ResponseHandler<HttpStatusCodes>();
        final HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put(FOUNDED_PROPERTY, new Date());

//        olingoApp.update(edm, TEST_MANUFACTURER_FOUNDED_PROPERTY, properties, statusHandler);
        // requires a plain Date for XML
        olingoApp.update(edm, TEST_MANUFACTURER_FOUNDED_PROPERTY, new Date(), statusHandler);

        LOG.info("Founded property updated with status {}", statusHandler.await().getStatusCode());

        statusHandler.reset();

        olingoApp.update(edm, TEST_MANUFACTURER_FOUNDED_VALUE, new Date(), statusHandler);

        LOG.info("Founded property updated with status {}", statusHandler.await().getStatusCode());

        // test complex property Manufacturer.Address
        propertyHandler.reset();

        olingoApp.read(edm, TEST_MANUFACTURER_ADDRESS_PROPERTY, null, propertyHandler);

        final Map<String, Object> address = propertyHandler.await();
        LOG.info("Address property {}", prettyPrint(address, 0));

        statusHandler.reset();

        address.clear();
        // Olingo2 sample server MERGE/PATCH behaves like PUT!!!
//        address.put("Street", "Main Street");
        address.put("Street", "Star Street 137");
        address.put("City", "Stuttgart");
        address.put("ZipCode", "70173");
        address.put("Country", "Germany");

//        olingoApp.patch(edm, TEST_MANUFACTURER_ADDRESS_PROPERTY, address, statusHandler);
        olingoApp.merge(edm, TEST_MANUFACTURER_ADDRESS_PROPERTY, address, statusHandler);

        LOG.info("Address property updated with status {}", statusHandler.await().getStatusCode());
    }

    @Test
    public void testReadLinks() throws Exception {
        final TestOlingo2ResponseHandler<List<String>> linksHandler = new TestOlingo2ResponseHandler<List<String>>();

        olingoApp.read(edm, TEST_MANUFACTURER_LINKS_CARS, null, linksHandler);

        final List<String> links = linksHandler.await();
        assertFalse(links.isEmpty());
        LOG.info("Read links: {}", links);

        final TestOlingo2ResponseHandler<String> linkHandler = new TestOlingo2ResponseHandler<String>();

        olingoApp.read(edm, TEST_CAR_LINK_MANUFACTURER, null, linkHandler);

        final String link = linkHandler.await();
        LOG.info("Read link: {}", link);
    }

    @Test
    public void testReadCount() throws Exception {
        final TestOlingo2ResponseHandler<Long> countHandler = new TestOlingo2ResponseHandler<Long>();

        olingoApp.read(edm, MANUFACTURERS + COUNT_OPTION, null, countHandler);

        LOG.info("Manufacturers count: {}", countHandler.await());

        countHandler.reset();
        olingoApp.read(edm, TEST_MANUFACTURER + COUNT_OPTION, null, countHandler);

        LOG.info("Manufacturer count: {}", countHandler.await());

        countHandler.reset();
        olingoApp.read(edm, TEST_MANUFACTURER_LINKS_CARS + COUNT_OPTION, null, countHandler);

        LOG.info("Manufacturers links count: {}", countHandler.await());

        countHandler.reset();
        olingoApp.read(edm, TEST_CAR_LINK_MANUFACTURER + COUNT_OPTION, null, countHandler);

        LOG.info("Manufacturer link count: {}", countHandler.await());
    }

    @Test
    public void testCreateUpdateDeleteEntry() throws Exception {

        // create entry to update
        final TestOlingo2ResponseHandler<ODataEntry> entryHandler = new TestOlingo2ResponseHandler<ODataEntry>();

        olingoApp.create(edm, MANUFACTURERS, getEntityData(), entryHandler);

        ODataEntry createdEntry = entryHandler.await();
        LOG.info("Created Entry:  {}", prettyPrint(createdEntry));

        Map<String, Object> data = getEntityData();
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) data.get(ADDRESS);

        data.put("Name", "MyCarManufacturer Renamed");
        address.put("Street", "Main Street");
        final TestOlingo2ResponseHandler<HttpStatusCodes> statusHandler =
            new TestOlingo2ResponseHandler<HttpStatusCodes>();

        olingoApp.update(edm, TEST_CREATE_MANUFACTURER, data, statusHandler);
        statusHandler.await();

        statusHandler.reset();
        data.put("Name", "MyCarManufacturer Patched");
        olingoApp.patch(edm, TEST_CREATE_MANUFACTURER, data, statusHandler);
        statusHandler.await();

        entryHandler.reset();
        olingoApp.read(edm, TEST_CREATE_MANUFACTURER, null, entryHandler);

        ODataEntry updatedEntry = entryHandler.await();
        LOG.info("Updated Entry successfully:  {}", prettyPrint(updatedEntry));

        statusHandler.reset();
        olingoApp.delete(TEST_CREATE_MANUFACTURER,  statusHandler);

        HttpStatusCodes statusCode = statusHandler.await();
        LOG.info("Deletion of Entry was successful:  {}: {}", statusCode.getStatusCode(), statusCode.getInfo());

        try {
            LOG.info("Verify Delete Entry");

            entryHandler.reset();
            olingoApp.read(edm, TEST_CREATE_MANUFACTURER, null, entryHandler);

            entryHandler.await();
            fail("Entry not deleted!");
        } catch (Exception e) {
            LOG.info("Deleted entry not found: {}", e.getMessage());
        }
    }

    @Test
    public void testBatchRequest() throws Exception {

        final List<Olingo2BatchRequest> batchParts = new ArrayList<Olingo2BatchRequest>();

        // Edm query
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(Olingo2AppImpl.METADATA).build());

        // feed query
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(MANUFACTURERS).build());

        // read
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_MANUFACTURER).build());

        // read with expand
        final HashMap<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(SystemQueryOption.$expand.toString(), CARS);
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_MANUFACTURER).queryParams(queryParams).build());

        // create
        final Map<String, Object> data = getEntityData();
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(MANUFACTURERS).
            contentId(TEST_RESOURCE_CONTENT_ID).operation(Operation.CREATE).body(data).build());

        // update
        final Map<String, Object> updateData = new HashMap<String, Object>(data);
        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) updateData.get(ADDRESS);
        updateData.put("Name", "MyCarManufacturer Renamed");
        address.put("Street", "Main Street");

        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE).operation(Operation.UPDATE)
            .body(updateData).build());

        // delete
        batchParts.add(Olingo2BatchChangeRequest.resourcePath(TEST_RESOURCE).operation(Operation.DELETE).build());

        final TestOlingo2ResponseHandler<List<Olingo2BatchResponse>> responseHandler =
            new TestOlingo2ResponseHandler<List<Olingo2BatchResponse>>();

        // read to verify delete
        batchParts.add(Olingo2BatchQueryRequest.resourcePath(TEST_CREATE_MANUFACTURER).build());

        olingoApp.batch(edm, batchParts, responseHandler);

        final List<Olingo2BatchResponse> responseParts = responseHandler.await(15, TimeUnit.MINUTES);
        assertEquals("Batch responses expected", 8, responseParts.size());

        assertNotNull(responseParts.get(0).getBody());
        final ODataFeed feed = (ODataFeed) responseParts.get(1).getBody();
        assertNotNull(feed);
        LOG.info("Batch feed:  {}", prettyPrint(feed));

        ODataEntry dataEntry = (ODataEntry) responseParts.get(2).getBody();
        assertNotNull(dataEntry);
        LOG.info("Batch read entry:  {}", prettyPrint(dataEntry));

        dataEntry = (ODataEntry) responseParts.get(3).getBody();
        assertNotNull(dataEntry);
        LOG.info("Batch read entry with expand:  {}", prettyPrint(dataEntry));

        dataEntry = (ODataEntry) responseParts.get(4).getBody();
        assertNotNull(dataEntry);
        LOG.info("Batch create entry:  {}", prettyPrint(dataEntry));

        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), responseParts.get(5).getStatusCode());
        assertEquals(HttpStatusCodes.NO_CONTENT.getStatusCode(), responseParts.get(6).getStatusCode());

        assertEquals(HttpStatusCodes.NOT_FOUND.getStatusCode(), responseParts.get(7).getStatusCode());
        final Exception exception = (Exception) responseParts.get(7).getBody();
        assertNotNull(exception);
        LOG.info("Batch retrieve deleted entry:  {}", exception);
    }

    private Map<String, Object> getEntityData() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("Id", "123");
        data.put("Name", "MyCarManufacturer");
        data.put(FOUNDED_PROPERTY, new Date());
        Map<String, Object> address = new HashMap<String, Object>();
        address.put("Street", "Main");
        address.put("ZipCode", "42421");
        address.put("City", "Fairy City");
        address.put("Country", "FarFarAway");
        data.put(ADDRESS, address);
        return data;
    }

    private static String prettyPrint(ODataFeed dataFeed) {
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");
        for (ODataEntry entry : dataFeed.getEntries()) {
            builder.append(prettyPrint(entry.getProperties(), 1)).append('\n');
        }
        builder.append("]\n");
        return builder.toString();
    }

    private static String prettyPrint(ODataEntry createdEntry) {
        return prettyPrint(createdEntry.getProperties(), 0);
    }

    private static String prettyPrint(Map<String, Object> properties, int level) {
        StringBuilder b = new StringBuilder();
        Set<Map.Entry<String, Object>> entries = properties.entrySet();

        for (Map.Entry<String, Object> entry : entries) {
            indent(b, level);
            b.append(entry.getKey()).append(": ");
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> objectMap = (Map<String, Object>) value;
                value = prettyPrint(objectMap, level + 1);
                b.append(value).append(NEW_LINE);
            } else if (value instanceof Calendar) {
                Calendar cal = (Calendar) value;
                value = SimpleDateFormat.getInstance().format(cal.getTime());
                b.append(value).append(NEW_LINE);
            } else if (value instanceof ODataDeltaFeed) {
                ODataDeltaFeed feed = (ODataDeltaFeed) value;
                List<ODataEntry> inlineEntries = feed.getEntries();
                b.append("{");
                for (ODataEntry oDataEntry : inlineEntries) {
                    value = prettyPrint(oDataEntry.getProperties(), level + 1);
                    b.append("\n[\n").append(value).append("\n],");
                }
                b.deleteCharAt(b.length() - 1);
                indent(b, level);
                b.append("}\n");
            } else {
                b.append(value).append(NEW_LINE);
            }
        }
        // remove last line break
        b.deleteCharAt(b.length() - 1);
        return b.toString();
    }

    private static void indent(StringBuilder builder, int indentLevel) {
        for (int i = 0; i < indentLevel; i++) {
            builder.append("  ");
        }
    }

    private static void generateSampleData(String serviceUrl) throws IOException {
        final HttpPost httpUriRequest = new HttpPost(serviceUrl.substring(0, serviceUrl.lastIndexOf('/')) + INDEX);
        httpUriRequest.setEntity(new ByteArrayEntity(GEN_SAMPLE_DATA.getBytes()));
        ((Olingo2AppImpl)olingoApp).execute(httpUriRequest, Olingo2AppImpl.APPLICATION_FORM_URL_ENCODED,
            new FutureCallback<HttpResponse>() {
                @Override
                public void completed(HttpResponse result) {
                    try {
                        AbstractFutureCallback.checkStatus(result);
                        LOG.info("Sample data generated  {}", result.getStatusLine());
                    } catch (ODataApplicationException e) {
                        LOG.error("Sample data generation error: " + e.getMessage(), e);
                    }
                }

                @Override
                public void failed(Exception ex) {
                    LOG.error("Error generating sample data " + ex.getMessage(), ex);
                }

                @Override
                public void cancelled() {
                    LOG.error("Sample data generation canceled!");
                }
            });
    }

    private static final class TestOlingo2ResponseHandler<T> implements Olingo2ResponseHandler<T> {

        private T response;
        private Exception error;
        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onResponse(T response) {
            this.response = response;
            if (LOG.isDebugEnabled()) {
                if (response instanceof ODataFeed) {
                    LOG.debug("Received response: {}", prettyPrint((ODataFeed) response));
                } else if (response instanceof ODataEntry) {
                    LOG.debug("Received response: {}", prettyPrint((ODataEntry) response));
                } else {
                    LOG.debug("Received response: {}", response);
                }
            }
            latch.countDown();
        }

        @Override
        public void onException(Exception ex) {
            error = ex;
            latch.countDown();
        }

        @Override
        public void onCanceled() {
            error = new IllegalStateException("Request Canceled");
            latch.countDown();
        }

        public T await() throws Exception {
            return await(TIMEOUT, TimeUnit.SECONDS);
        }

        public T await(long timeout, TimeUnit unit) throws Exception {
            assertTrue("Timeout waiting for response", latch.await(timeout, unit));
            if (error != null) {
                throw error;
            }
            assertNotNull("Response", response);
            return response;
        }

        public void reset() {
            latch.countDown();
            latch = new CountDownLatch(1);
            response = null;
            error = null;
        }
    }
}