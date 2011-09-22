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

import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.subsystem.test.ModelDescriptionValidator;
import org.jboss.as.subsystem.test.ModelDescriptionValidator.ValidationFailure;
import org.jboss.dmr.ModelNode;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TypeValidator3 {
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
//            System.out.println(model);
//            System.out.println("-----------------");

            ModelDescriptionValidator validator = new ModelDescriptionValidator(address, model, null);
            List<ValidationFailure>  errors = validator.validateResource();
            if (errors.size() == 0) {
                System.out.println("OK");
            } else {
                for (ValidationFailure error : errors) {
                    System.out.println(error);
                }
            }

        } finally {
            IoUtils.safeClose(client);
        }
    }

}
