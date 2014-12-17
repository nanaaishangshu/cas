/*
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.cas.services.support;

import org.jasig.cas.authentication.principal.Principal;
import org.jasig.cas.authentication.principal.Service;
import org.jasig.cas.services.RegisteredServiceAuthorizationStrategy;
import org.jasig.cas.services.UnauthorizedServiceException;
import org.jasig.cas.services.UnauthorizedServiceMissingAttributeException;
import org.jasig.cas.services.UnauthorizedSsoServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This is {@link org.jasig.cas.services.support.DefaultRegisteredServiceAuthorizationStrategy}
 * that allows the following rules:
 *
 * <ul>
 *     <li>A service may be disallowed to use CAS for authentication</li>
 *     <li>A service may be disallowed to take part in CAS single sign-on such that
 *     presentation of credentials would always be required.</li>
 *     <li>A service may be prohibited from receiving a service ticket
 *     if the existing principal attributes don't contain the required attributes
 *     that otherwise grant access to the service.</li>
 * </ul>
 *
 * @author Misagh Moayyed mmoayyed@unicon.net
 * @since 4.1
 */
public class DefaultRegisteredServiceAuthorizationStrategy implements RegisteredServiceAuthorizationStrategy {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** Is the service allowed at all? **/
    private boolean enabled;

    /** Is the service allowed to use SSO? **/
    private boolean ssoEnabled;

    /**
     * Defines the attribute aggregation behavior when checking for required attributes.
     * Default requires that all attributes be present and match the principal's.
     */
    private boolean requireAllAttributes = true;

    /**
     * Collection of required attributes
     * for this service to proceed.
     */
    private Map<String, List<String>> requiredAttributes = new TreeMap<>();

    /**
     * Instantiates a new Default registered service authorization strategy.
     * By default, rules indicate that services are both enabled
     * and can participate in SSO.
     */
    public DefaultRegisteredServiceAuthorizationStrategy() {
        this.enabled = true;
        this.ssoEnabled = true;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public void setSsoEnabled(final boolean ssoEnabled) {
        this.ssoEnabled = ssoEnabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isSsoEnabled() {
        return this.ssoEnabled;
    }


    public Map<String, List<String>> getRequiredAttributes() {
        return requiredAttributes;
    }

    /**
     * Defines the attribute aggregation when checking for required attributes.
     * Default requires that all attributes be present and match the principal's.
     * @param requireAllAttributes the require all attributes
     */
    public void setRequireAllAttributes(final boolean requireAllAttributes) {
        this.requireAllAttributes = requireAllAttributes;
    }

    /**
     * Defines the required attribute names and values that
     * must be available to the principal before the flow
     * can proceed to the next step. Every attribute in
     * the map can be linked to multiple values.
     *
     * @param requiredAttributes the required attributes
     */
    public void setRequiredAttributes(final Map<String, List<String>> requiredAttributes) {
        this.requiredAttributes = requiredAttributes;
    }

    /**
     * {@inheritDoc}
     *
     * Verify presence of service required attributes.
     * <ul>
     *     <li>If no required attributes are specified, authz is granted.</li>
     *     <li>If ALL required attributes must be present, and the principal contains all and there is
     *     at least one attribute value that matches the required, authz is granted.</li>
     *     <li>If ALL required attributes don't have to be present, and there is at least
     *     one principal attribute present whose value matches the required, authz is granted.</li>
     *     <li>Otherwise, access is denied and an exception thrown</li>
     * </ul>
     */
    @Override
    public void assertServiceAuthorizedAccessForPrincipal(final Principal principal, final Service service) {
        if (this.requiredAttributes.isEmpty()) {
            logger.debug("No required attributes are specified");
            return;
        }
        final Map<String, Object> principalAttributes = principal.getAttributes();
        if (principalAttributes.isEmpty()) {
            throw new UnauthorizedServiceMissingAttributeException("No principal attributes are found to satisfy requirements");
        }

        logger.debug("These attributes [{}] are examined against [{}] before service [{}] can proceed.",
                this.requiredAttributes, principalAttributes, service.getId());

        final Set<Map.Entry<String, List<String>>> entrySetOfRequiredAttributes = requiredAttributes.entrySet();

        for (final Map.Entry<String, List<String>> entry : entrySetOfRequiredAttributes) {
            final String requiredAttributeName = entry.getKey();
            final List<String> requiredAttributeValues = entry.getValue();

            final Object principalAttributeValue = principalAttributes.get(requiredAttributeName);

            if (principalAttributeValue == null && this.requireAllAttributes) {
                throw new UnauthorizedServiceMissingAttributeException("Principal is missing the required attribute "
                        + requiredAttributeName);
            }
            boolean foundMatchingAttributeValue = false;

            if (principalAttributeValue != null) {
                final Iterator<String> it = requiredAttributeValues.iterator();
                while (!foundMatchingAttributeValue && it.hasNext()) {
                    final String value = it.next();
                    if (principalAttributeValue instanceof Collection) {
                        final Collection principalAttributeValueAsCol = (Collection) principalAttributes;
                        foundMatchingAttributeValue = principalAttributeValueAsCol.contains(value);
                    } else {
                        foundMatchingAttributeValue = value.equals(principalAttributeValue);
                    }
                }
            }

            if (!foundMatchingAttributeValue) {
                throw new UnauthorizedServiceMissingAttributeException("None of the required attributes match "
                        + principalAttributeValue);
            }
        }

    }

    @Override
    public void assertServiceSsoParticipation(@NotNull final Service service) {
        if (!isSsoEnabled()) {
            final String msg = String.format("Service [%s] is not allowed to participate in SSO.", service.getId());
            logger.warn(msg);
            throw new UnauthorizedSsoServiceException(msg);
        }
    }

    @Override
    public void assertServiceQualification(final Service service) {
        if (!isEnabled()) {
            final String msg = String.format("Service [%s] is not enabled in service registry.", service.getId());
            logger.warn(msg);
            throw new UnauthorizedServiceException(UnauthorizedServiceException.CODE_UNAUTHZ_SERVICE, msg);
        }
    }


}
