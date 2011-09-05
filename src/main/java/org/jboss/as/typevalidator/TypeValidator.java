/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.typevalidator;

import static org.jboss.as.controller.client.helpers.ClientConstants.NAME;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TypeValidator {

    ModelControllerClient client;
    List<String> failures = new ArrayList<String>();

    private TypeValidator(ModelControllerClient client) {
        this.client = client;
    }

    public static void main(String[] args) throws Exception {
        ModelControllerClient client = ModelControllerClient.Factory.create("localhost", 9999);
        try {
            ModelNode addr = new ModelNode();
            addr.setEmptyList();
            TypeValidator validator = new TypeValidator(client);
            validator.validateSubtree(addr);

            for (String failure : validator.failures) {
                System.out.println(failure);
            }
        } finally {
            IoUtils.safeClose(client);
        }
    }

    private void validateSubtree(ModelNode address) throws Exception {

        validateAttributes(address);
        validateOperations(address);
        validateChildren(address);
    }

    private void validateChildren(ModelNode address) throws Exception {
        ModelNode readChildTypes = new ModelNode();
        readChildTypes.get(OP).set("read-children-types");
        readChildTypes.get(OP_ADDR).set(address);
        for (ModelNode childType : client.execute(readChildTypes).require(RESULT).asList()){

            ModelNode readChildrenNames = new ModelNode();
            readChildrenNames.get(OP).set("read-children-names");
            readChildrenNames.get(OP_ADDR).set(address);
            readChildrenNames.get("child-type").set(childType.asString());
            for (ModelNode childName : client.execute(readChildrenNames).require(RESULT).asList()) {
                validateSubtree(address.clone().add(childType.asString(), childName.asString()));
            }
        }
    }

    private void validateAttributes(ModelNode address) throws Exception {
        ModelNode readResourceDescription = new ModelNode();
        readResourceDescription.get(OP).set("read-resource-description");
        readResourceDescription.get(OP_ADDR).set(address);

        ModelNode attributes = client.execute(readResourceDescription).require(RESULT).get("attributes");
        if (!attributes.isDefined()) {
            return;
        }
        for (String key : attributes.keys()) {
            validateType("'attribute " + key + "'", address, attributes.get(key), attributes.get(key));
        }
    }

    private void validateOperations(ModelNode address) throws Exception {
        ModelNode readOperationNames = new ModelNode();
        readOperationNames.get(OP).set("read-operation-names");
        readOperationNames.get(OP_ADDR).set(address);
        for (ModelNode opName : client.execute(readOperationNames).require(RESULT).asList()) {
            ModelNode readOperationDescription = new ModelNode();
            readOperationDescription.get(OP).set("read-operation-description");
            readOperationDescription.get(OP_ADDR).set(address);
            readOperationDescription.get(NAME).set(opName.asString());

            ModelNode params = client.execute(readOperationDescription).require(RESULT).get("request-properties");
            if (!params.isDefined()) {
                continue;
            }
            for (String key : params.keys()) {
                validateType("'parameter " + key + " of " + opName.asString() + "'", address, params.get(key), params.get(key));
            }
        }
    }

    private void validateType(String msg, ModelNode address, ModelNode rawAttribute, ModelNode attribute) {
        if (!attribute.get("type").isDefined()) {
            failures.add("*No type for:" + msg + " at " + address + " " + rawAttribute);
            return;
        }

        ModelType type;
        try {
            type = attribute.get("type").asType();
        } catch (Exception e){
            failures.add("*Can't create type for " + msg + " at " + address + " " + rawAttribute);
            return;
        }

        if (type == ModelType.OBJECT || type == ModelType.LIST) {
            if (!attribute.get("value-type").isDefined()) {
                failures.add("*No value-type for " + type + " " + msg + " at " + address + " " + rawAttribute);
                return;
            }
            validateValueType(msg, address, rawAttribute, attribute.get("value-type"));
        } else {
            if (attribute.get("value-type").isDefined()) {
                failures.add("*Value-type given for " + type + " " + msg + " at " + address + " " + rawAttribute);
            }
        }
    }

    private void validateValueType(String msg, ModelNode address, ModelNode rawAttribute, ModelNode valueType) {

        try {
            valueType.asType();
        } catch (Exception e) {
            //Not a simple type
            for (String key : valueType.keys()) {
                validateType(msg, address, rawAttribute, valueType.get(key));
            }
        }
    }
}
