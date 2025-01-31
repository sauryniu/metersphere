package io.metersphere.api.parser.api.dataimport;

import io.metersphere.api.constants.ApiScenarioStatus;
import io.metersphere.api.constants.ApiScenarioStepRefType;
import io.metersphere.api.constants.ApiScenarioStepType;
import io.metersphere.api.dto.request.MsJMeterComponent;
import io.metersphere.api.dto.request.controller.*;
import io.metersphere.api.dto.request.http.MsHTTPElement;
import io.metersphere.api.dto.scenario.ApiScenarioImportDetail;
import io.metersphere.api.dto.scenario.ApiScenarioImportRequest;
import io.metersphere.api.dto.scenario.ApiScenarioStepRequest;
import io.metersphere.api.parser.ApiScenarioImportParser;
import io.metersphere.api.parser.jmeter.xstream.MsSaveService;
import io.metersphere.api.parser.ms.MsTestElementParser;
import io.metersphere.plugin.api.spi.AbstractMsProtocolTestElement;
import io.metersphere.plugin.api.spi.AbstractMsTestElement;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.uid.IDGenerator;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jorphan.collections.HashTree;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

public class JmeterParserApiScenario implements ApiScenarioImportParser {


    @Override
    public List<ApiScenarioImportDetail> parse(InputStream inputSource, ApiScenarioImportRequest request) throws Exception {
        try {
            Object scriptWrapper = MsSaveService.loadElement(inputSource);
            HashTree hashTree = this.getHashTree(scriptWrapper);
            MsTestElementParser parser = new MsTestElementParser();
            AbstractMsTestElement msTestElement = parser.parse(hashTree);
            Map<String, String> polymorphicNameMap = parser.getPolymorphicNameMap(request.getProjectId());
            String scenarioName = StringUtils.trim(parser.parseTestPlanName(hashTree));
            return Collections.singletonList(this.parseImportFile(request.getProjectId(), msTestElement, polymorphicNameMap, scenarioName));
        } catch (Exception e) {
            LogUtils.error(e);
            throw new MSException("当前JMX版本不兼容");
        }
    }

    private ApiScenarioImportDetail parseImportFile(String projectId, AbstractMsTestElement msElementList, Map<String, String> polymorphicNameMap, String scenarioName) {
        ApiScenarioImportDetail apiScenarioDetail = new ApiScenarioImportDetail();
        apiScenarioDetail.setName(scenarioName);
        apiScenarioDetail.setPriority("P0");
        apiScenarioDetail.setStatus(ApiScenarioStatus.UNDERWAY.name());
        apiScenarioDetail.setGrouped(false);
        apiScenarioDetail.setDeleted(false);
        apiScenarioDetail.setLatest(true);
        apiScenarioDetail.setProjectId(projectId);

        ApiScenarioStepParseResult stepParseResult = this.parseScenarioStep(msElementList.getChildren(), projectId, polymorphicNameMap);

        apiScenarioDetail.setSteps(stepParseResult.getStepList());
        apiScenarioDetail.setStepDetails(stepParseResult.getStepDetails());

        apiScenarioDetail.setStepTotal(CollectionUtils.size(apiScenarioDetail.getSteps()));
        return apiScenarioDetail;
    }

    private ApiScenarioStepParseResult parseScenarioStep(List<AbstractMsTestElement> msElementList, String projectId, Map<String, String> polymorphicNameMap) {
        ApiScenarioStepParseResult parseResult = new ApiScenarioStepParseResult();
        for (AbstractMsTestElement msTestElement : msElementList) {
            ApiScenarioStepRequest apiScenarioStep = new ApiScenarioStepRequest();
            apiScenarioStep.setId(IDGenerator.nextStr());
            apiScenarioStep.setProjectId(projectId);
            apiScenarioStep.setName(msTestElement.getName());
            apiScenarioStep.setUniqueId(IDGenerator.nextStr());
            msTestElement.setStepId(apiScenarioStep.getId());
            msTestElement.setProjectId(apiScenarioStep.getProjectId());
            byte[] stepBlobContent = null;
            if (msTestElement instanceof MsHTTPElement msHTTPElement) {
                apiScenarioStep.setConfig(new ProtocolConfig("HTTP", msHTTPElement.getMethod()));
                apiScenarioStep.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
                apiScenarioStep.setRefType(ApiScenarioStepRefType.DIRECT.name());
                msHTTPElement.setCustomizeRequest(true);
                stepBlobContent = JSON.toJSONString(msTestElement).getBytes();
            } else if (msTestElement instanceof AbstractMsProtocolTestElement msProtocolTestElement) {
                apiScenarioStep.setStepType(ApiScenarioStepType.CUSTOM_REQUEST.name());
                msProtocolTestElement.setCustomizeRequest(true);
                String protocol = polymorphicNameMap.get(msTestElement.getClass().getSimpleName());
                apiScenarioStep.setConfig(new ProtocolConfig(protocol, protocol));
                apiScenarioStep.setRefType(ApiScenarioStepRefType.DIRECT.name());
                stepBlobContent = JSON.toJSONString(msTestElement).getBytes();
            } else if (msTestElement instanceof MsJMeterComponent) {
                apiScenarioStep.setStepType(this.getStepType(msTestElement));
                apiScenarioStep.setConfig(new HashMap<>());
                apiScenarioStep.setRefType(ApiScenarioStepRefType.DIRECT.name());
            } else {
                apiScenarioStep.setStepType(this.getStepType(msTestElement));
                apiScenarioStep.setConfig(JSON.toJSONString(msTestElement));
            }

            parseResult.getStepList().add(apiScenarioStep);
            if (stepBlobContent != null) {
                parseResult.getStepDetails().put(apiScenarioStep.getId(), stepBlobContent);
            }

            if (!(msTestElement instanceof AbstractMsProtocolTestElement) && CollectionUtils.isNotEmpty(msTestElement.getChildren())) {
                //非请求类型组件，继续处理子组件
                ApiScenarioStepParseResult childParseResult = parseScenarioStep(msTestElement.getChildren(), projectId, polymorphicNameMap);
                apiScenarioStep.setChildren(childParseResult.getStepList());
                if (MapUtils.isNotEmpty(childParseResult.getStepDetails())) {
                    parseResult.getStepDetails().putAll(childParseResult.getStepDetails());
                }
            }
        }
        return parseResult;
    }

    private String getStepType(AbstractMsTestElement msTestElement) {
        if (msTestElement instanceof MsLoopController) {
            return ApiScenarioStepType.LOOP_CONTROLLER.name();
        } else if (msTestElement instanceof MsOnceOnlyController) {
            return ApiScenarioStepType.ONCE_ONLY_CONTROLLER.name();
        } else if (msTestElement instanceof MsIfController) {
            return ApiScenarioStepType.IF_CONTROLLER.name();
        } else if (msTestElement instanceof MsConstantTimerController) {
            return ApiScenarioStepType.CONSTANT_TIMER.name();
        } else if (msTestElement instanceof MsScriptElement) {
            return ApiScenarioStepType.SCRIPT.name();
        } else {
            return ApiScenarioStepType.JMETER_COMPONENT.name();
        }
    }

    private HashTree getHashTree(Object scriptWrapper) throws Exception {
        Field field = scriptWrapper.getClass().getDeclaredField("testPlan");
        field.setAccessible(true);
        return (HashTree) field.get(scriptWrapper);
    }
}

@Data
class ApiScenarioStepParseResult {
    private List<ApiScenarioStepRequest> stepList = new ArrayList<>();
    private Map<String, Object> stepDetails = new HashMap<>();
}

class ProtocolConfig {
    String id;
    String name;
    boolean enable = true;
    String protocol;
    String method;

    public ProtocolConfig(String protocol, String method) {
        this.protocol = protocol;
        this.method = method;
    }
}
