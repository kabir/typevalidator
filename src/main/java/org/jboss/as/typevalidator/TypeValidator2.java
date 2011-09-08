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

import static org.jboss.as.controller.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TypeValidator2 {

    final ModelNode address;
    final ModelNode model;

    private TypeValidator2(ModelNode address, ModelNode model) {
        this.address = address;
        this.model = model;
        //System.out.println(model);
    }

    public static void main(String[] args) throws Exception {

        ModelNode address = new ModelNode();
        address.setEmptyList();
        address.add("subsystem", "messaging");

        ModelNode op = new ModelNode();
        op.get(OP).set("read-resource-description");
        //op.get(OP_ADDR).setEmptyList();
        op.get(OP_ADDR).set(address);
        op.get("recursive").set(true);
        op.get("inherited").set(false);
        op.get("operations").set(true);

        ModelControllerClient client = ModelControllerClient.Factory.create("localhost", 9999);
        try {
            ModelNode result = client.execute(op);
            if (result.hasDefined(FAILURE_DESCRIPTION)) {
                throw new Exception(result.get(FAILURE_DESCRIPTION).asString());
            }

            ModelNode model = result.get(RESULT);
            System.out.println(model);
            System.out.println("-----------------");

            TypeValidator2 validator = new TypeValidator2(address, model);
            validator.validate();

        } finally {
            IoUtils.safeClose(client);
        }
    }

    private void validate() {
        if (model.hasDefined("attributes")) {
            validateAttributes(model.get("attributes"));
        }
        if (model.hasDefined("operations")) {
            validateOperations(model.get("operations"));
        }
        if (model.hasDefined("children")) {
            validateChildren(model.get("children"));
        }
    }

    private void validateAttributes(ModelNode attributes) {
        //System.out.println(attributes);
        for (String key : attributes.keys()) {
            validateValue("Attribute '" + key + "'", attributes.get(key));
        }
    }

    private void validateOperations(ModelNode operations) {

        for (String key : operations.keys()) {
            ModelNode op = operations.get(key);
            //System.out.println(op);
            //TODO validate operation name
            //Some places use operation others use operation-name
            //if (!key.equals(op.get("operation-name").asString())) {
            //    System.out.println("Expected operation name '" + key + "' for " + op + "@" + address);
            //}
            if (op.hasDefined("request-properties")) {
                for (String param : op.get("request-properties").keys()) {
                    validateValue("Param '" + param + "' for '" + key + "'", op.get("request-properties", param));
                }
            }
        }
    }

    private void validateChildren(ModelNode children) {
        //System.out.println(children);
        for (String type : children.keys()) {
            //System.out.println(type);
            ModelNode childType = children.get(type, "model-description");
            if (!childType.isDefined()) {
                System.out.println("No model description for child '" + type + "' @" + address);
                continue;
            }

            for (String child : childType.keys()) {
                ModelNode childAddress = address.clone();
                childAddress.add(type, child);
                TypeValidator2 childValidator = new TypeValidator2(childAddress, childType.get(child));
                childValidator.validate();
            }
        }
    }

    private void validateValue(String description, ModelNode rawDescription) {
        validateValue(description, rawDescription, rawDescription);
    }

    private void validateValue(String description, ModelNode rawDescription, ModelNode currentDescription) {
        ModelNode typeNode = currentDescription.get("type");
        ModelNode valueTypeNode = currentDescription.get("value-type");

        ModelType type;
        try {
            type = typeNode.asType();
        } catch (Exception e) {
            logError("Invalid type in " + description, currentDescription);
            return;
        }

        if (type == ModelType.OBJECT || type == ModelType.LIST) {
            if (!valueTypeNode.isDefined()) {
                logError("No value-type for type=" + type + " in " + description, currentDescription);
                return;
            }
        } else {
            if (valueTypeNode.isDefined()) {
                logError("Unnecessary value-type for type=" + type + " in " + description, currentDescription);
            }
            return;
        }


        ModelType valueType;
        try {
            valueType = valueTypeNode.asType();
        } catch (Exception e) {
            //Complex type
            for (String key : valueTypeNode.keys()) {
                validateValue(description, rawDescription, valueTypeNode.get(key));
            }
        }
    }

    private void logError(String error, ModelNode currentDescription) {
        System.out.println(error + "@" + address + " " + currentDescription);
    }
}
