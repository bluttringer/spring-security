package org.springframework.security.config;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.security.providers.ProviderManager;
import org.springframework.security.userdetails.UserDetailsService;
import org.springframework.security.vote.AffirmativeBased;
import org.springframework.security.vote.AuthenticatedVoter;
import org.springframework.security.vote.RoleVoter;
import org.springframework.util.StringUtils;

/**
 * Utility methods used internally by the Spring Security namespace configuration code.
 *
 * @author Luke Taylor
 * @author Ben Alex
 * @version $Id$
 */
public abstract class ConfigUtils {
    private static final Log logger = LogFactory.getLog(ConfigUtils.class);

    static void registerDefaultAccessManagerIfNecessary(ParserContext parserContext) {

        if (!parserContext.getRegistry().containsBeanDefinition(BeanIds.ACCESS_MANAGER)) {
            ManagedList defaultVoters = new ManagedList(2);

            defaultVoters.add(new RootBeanDefinition(RoleVoter.class));
            defaultVoters.add(new RootBeanDefinition(AuthenticatedVoter.class));

            BeanDefinitionBuilder accessMgrBuilder = BeanDefinitionBuilder.rootBeanDefinition(AffirmativeBased.class);
            accessMgrBuilder.addPropertyValue("decisionVoters", defaultVoters);
            BeanDefinition accessMgr = accessMgrBuilder.getBeanDefinition();

            parserContext.getRegistry().registerBeanDefinition(BeanIds.ACCESS_MANAGER, accessMgr);
        }
    }
    
    public static int countNonEmpty(String[] objects) {        
    	int nonNulls = 0;
    	
    	for (int i = 0; i < objects.length; i++) {
    		if (StringUtils.hasText(objects[i])) {
    			nonNulls++;
    		}
    	}
        
    	return nonNulls;
    }

    public static void addVoter(BeanDefinition voter, ParserContext parserContext) {
        registerDefaultAccessManagerIfNecessary(parserContext);

        BeanDefinition accessMgr = parserContext.getRegistry().getBeanDefinition(BeanIds.ACCESS_MANAGER);

        ManagedList voters = (ManagedList) accessMgr.getPropertyValues().getPropertyValue("decisionVoters").getValue();
        voters.add(voter);
        
        accessMgr.getPropertyValues().addPropertyValue("decisionVoters", voters);
    }

    /**
     * Creates and registers the bean definition for the default ProviderManager instance and returns
     * the BeanDefinition for it. This method will typically be called when registering authentication providers
     * using the &lt;security:provider /> tag or by other beans which have a dependency on the
     * authentication manager.
     */
    static BeanDefinition registerProviderManagerIfNecessary(ParserContext parserContext) {
        if(parserContext.getRegistry().containsBeanDefinition(BeanIds.AUTHENTICATION_MANAGER)) {
            return parserContext.getRegistry().getBeanDefinition(BeanIds.AUTHENTICATION_MANAGER);
        }

        BeanDefinition authManager = new RootBeanDefinition(ProviderManager.class);
        authManager.getPropertyValues().addPropertyValue("providers", new ManagedList());
        parserContext.getRegistry().registerBeanDefinition(BeanIds.AUTHENTICATION_MANAGER, authManager);

        return authManager;
    }

    /**
     * Obtains a user details service for use in RememberMeServices etc. Will return a caching version
     * if available so should not be used for beans which need to separate the two. 
     */
    static RuntimeBeanReference getUserDetailsService(ConfigurableListableBeanFactory bf) {
        String[] services = bf.getBeanNamesForType(CachingUserDetailsService.class, false, false);
        
        if (services.length == 0) {
        	services = bf.getBeanNamesForType(UserDetailsService.class);
        }

        if (services.length == 0) {
            throw new IllegalArgumentException("No UserDetailsService registered.");

        } else if (services.length > 1) {
            throw new IllegalArgumentException("More than one UserDetailsService registered. Please " +
                    "use a specific Id in your configuration");
        }

        return new RuntimeBeanReference(services[0]);
    }

    static ManagedList getRegisteredProviders(ParserContext parserContext) {
        BeanDefinition authManager = registerProviderManagerIfNecessary(parserContext);
        return (ManagedList) authManager.getPropertyValues().getPropertyValue("providers").getValue();
    }
    
    private static void registerFilterChainPostProcessorIfNecessary(ParserContext pc) {
    	if (pc.getRegistry().containsBeanDefinition(BeanIds.FILTER_CHAIN_POST_PROCESSOR)) {
    		return;
    	}
        // Post processor specifically to assemble and order the filter chain immediately before the FilterChainProxy is initialized.
        RootBeanDefinition filterChainPostProcessor = new RootBeanDefinition(FilterChainProxyPostProcessor.class);
        filterChainPostProcessor.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        pc.getRegistry().registerBeanDefinition(BeanIds.FILTER_CHAIN_POST_PROCESSOR, filterChainPostProcessor);
        RootBeanDefinition filterList = new RootBeanDefinition(FilterChainList.class);
        filterList.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        pc.getRegistry().registerBeanDefinition(BeanIds.FILTER_LIST, filterList);
    }
    
    static void addHttpFilter(ParserContext pc, BeanMetadataElement filter) {
    	registerFilterChainPostProcessorIfNecessary(pc);
    	
    	RootBeanDefinition filterList = (RootBeanDefinition) pc.getRegistry().getBeanDefinition(BeanIds.FILTER_LIST);
    	
    	ManagedList filters;
    	MutablePropertyValues pvs = filterList.getPropertyValues();
    	if (pvs.contains("filters")) {
    		filters = (ManagedList) pvs.getPropertyValue("filters").getValue();
    	} else {
    		filters = new ManagedList();
    		pvs.addPropertyValue("filters", filters);
    	}
    	
    	filters.add(filter);
    }

    /**
     * Bean which holds the list of filters which are maintained in the context and modified by calls to 
     * addHttpFilter. The post processor retrieves these before injecting the list into the FilterChainProxy.
     */
    public static class FilterChainList {
    	List filters;

		public List getFilters() {
			return filters;
		}

		public void setFilters(List filters) {
			this.filters = filters;
		}
    }    
}