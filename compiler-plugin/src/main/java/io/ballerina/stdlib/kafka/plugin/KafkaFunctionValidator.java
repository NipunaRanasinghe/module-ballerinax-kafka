/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.kafka.plugin;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.ClassSymbol;
import io.ballerina.compiler.api.symbols.MethodSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.RecordFieldSymbol;
import io.ballerina.compiler.api.symbols.RecordTypeSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.ArrayTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.IntersectionTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Location;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.ballerina.compiler.api.symbols.TypeDescKind.ANYDATA;
import static io.ballerina.compiler.api.symbols.TypeDescKind.ARRAY;
import static io.ballerina.compiler.api.symbols.TypeDescKind.BOOLEAN;
import static io.ballerina.compiler.api.symbols.TypeDescKind.DECIMAL;
import static io.ballerina.compiler.api.symbols.TypeDescKind.FLOAT;
import static io.ballerina.compiler.api.symbols.TypeDescKind.INT;
import static io.ballerina.compiler.api.symbols.TypeDescKind.JSON;
import static io.ballerina.compiler.api.symbols.TypeDescKind.MAP;
import static io.ballerina.compiler.api.symbols.TypeDescKind.NIL;
import static io.ballerina.compiler.api.symbols.TypeDescKind.RECORD;
import static io.ballerina.compiler.api.symbols.TypeDescKind.STRING;
import static io.ballerina.compiler.api.symbols.TypeDescKind.TABLE;
import static io.ballerina.compiler.api.symbols.TypeDescKind.TYPE_REFERENCE;
import static io.ballerina.compiler.api.symbols.TypeDescKind.UNION;
import static io.ballerina.compiler.api.symbols.TypeDescKind.XML;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.ANYDATA_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.ARRAY_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.BOOLEAN_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.BYTE_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.DECIMAL_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.ERROR_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.FLOAT_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.INT_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.JSON_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.MAP_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.NIL_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.QUALIFIED_NAME_REFERENCE;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.RECORD_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.SIMPLE_NAME_REFERENCE;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.STRING_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.TABLE_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.UNION_TYPE_DESC;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.XML_TYPE_DESC;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CALLER;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.FUNCTION_SHOULD_BE_REMOTE;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.INVALID_PARAM_COUNT;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.INVALID_PARAM_TYPES;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.INVALID_RETURN_TYPE_ERROR_OR_NIL;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.INVALID_SINGLE_PARAMETER;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.MUST_HAVE_CALLER_AND_RECORDS;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.MUST_HAVE_ERROR;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.NO_ON_CONSUMER_RECORD;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.CompilationErrors.ONLY_ERROR_ALLOWED;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.ERROR_PARAM;
import static io.ballerina.stdlib.kafka.plugin.PluginConstants.PAYLOAD_ANNOTATION;
import static io.ballerina.stdlib.kafka.plugin.PluginUtils.getDiagnostic;
import static io.ballerina.stdlib.kafka.plugin.PluginUtils.getMethodSymbol;
import static io.ballerina.stdlib.kafka.plugin.PluginUtils.validateModuleId;
import static io.ballerina.tools.diagnostics.DiagnosticSeverity.ERROR;

/**
 * Kafka remote function validator.
 */
public class KafkaFunctionValidator {

    private final SyntaxNodeAnalysisContext context;
    private final ServiceDeclarationNode serviceDeclarationNode;
    private final SemanticModel semanticModel;
    FunctionDefinitionNode onConsumerRecord;
    FunctionDefinitionNode onError;

    public KafkaFunctionValidator(SyntaxNodeAnalysisContext context, FunctionDefinitionNode onConsumerRecord,
                                  FunctionDefinitionNode onError) {
        this.context = context;
        this.serviceDeclarationNode = (ServiceDeclarationNode) context.node();
        this.onConsumerRecord = onConsumerRecord;
        this.onError = onError;
        this.semanticModel = context.semanticModel();
    }

    public void validate() {
        validateMandatoryFunction();
        if (Objects.nonNull(onConsumerRecord)) {
            validateOnConsumerRecord();
        }
        if (Objects.nonNull(onError)) {
            validateOnError();
        }
    }

    private void validateMandatoryFunction() {
        if (Objects.isNull(onConsumerRecord)) {
            reportErrorDiagnostic(NO_ON_CONSUMER_RECORD, serviceDeclarationNode.location());
        }
    }

    private void validateOnConsumerRecord() {
        if (!PluginUtils.isRemoteFunction(context, onConsumerRecord)) {
            reportErrorDiagnostic(FUNCTION_SHOULD_BE_REMOTE, onConsumerRecord.functionSignature().location());
        }
        validateOnConsumerRecordParameters(onConsumerRecord);
        validateReturnTypeErrorOrNil(onConsumerRecord);
    }

    private void validateOnError() {
        if (!PluginUtils.isRemoteFunction(context, onError)) {
            reportErrorDiagnostic(FUNCTION_SHOULD_BE_REMOTE, onError.functionSignature().location());
        }
        validateOnErrorParameters(onError);
        validateReturnTypeErrorOrNil(onError);
    }

    private void validateOnErrorParameters(FunctionDefinitionNode functionDefinitionNode) {
        SeparatedNodeList<ParameterNode> parameters = functionDefinitionNode.functionSignature().parameters();
        if (parameters.size() == 1) {
            ParameterNode paramNode = parameters.get(0);
            SyntaxKind paramSyntaxKind = ((RequiredParameterNode) paramNode).typeName().kind();
            if (paramSyntaxKind.equals(QUALIFIED_NAME_REFERENCE)) {
                Node parameterTypeNode = ((RequiredParameterNode) paramNode).typeName();
                Optional<Symbol> paramSymbol = semanticModel.symbol(parameterTypeNode);
                if (!paramSymbol.get().getName().get().equals(ERROR_PARAM) ||
                        !validateModuleId(paramSymbol.get().getModule().get())) {
                    reportErrorDiagnostic(ONLY_ERROR_ALLOWED, paramNode.location());
                }
            } else if (!paramSyntaxKind.equals(ERROR_TYPE_DESC)) {
                reportErrorDiagnostic(ONLY_ERROR_ALLOWED, paramNode.location());
            }
        } else if (parameters.size() > 1) {
            reportErrorDiagnostic(ONLY_ERROR_ALLOWED, functionDefinitionNode.functionSignature().location());
        } else {
            reportErrorDiagnostic(MUST_HAVE_ERROR, functionDefinitionNode.functionSignature().location());
        }
    }

    private void validateOnConsumerRecordParameters(FunctionDefinitionNode functionDefinitionNode) {
        SeparatedNodeList<ParameterNode> parameters = functionDefinitionNode.functionSignature().parameters();
        Location location = functionDefinitionNode.functionSignature().location();
        if (parameters.size() > 3) {
            reportErrorDiagnostic(INVALID_PARAM_COUNT, location);
            return;
        } else if (parameters.size() < 1) {
             reportErrorDiagnostic(MUST_HAVE_CALLER_AND_RECORDS, location);
             return;
        }
        validateParameterTypes(parameters, location);
    }

    private void validateParameterTypes(SeparatedNodeList<ParameterNode> parameters, Location location) {
        boolean callerExists = false;
        boolean consumerRecordsExists = false;
        boolean payloadExists = false;
        for (ParameterNode paramNode: parameters) {
            boolean tempConsumerRecordFlag = false;
            boolean tempPayloadFlag = false;
            RequiredParameterNode requiredParameterNode = (RequiredParameterNode) paramNode;
            SyntaxKind paramSyntaxKind = requiredParameterNode.typeName().kind();
            switch (paramSyntaxKind) {
                case ARRAY_TYPE_DESC:
                    if (!consumerRecordsExists) {
                        tempConsumerRecordFlag = validateConsumerRecordsParam(requiredParameterNode);
                    }
                    if (!tempConsumerRecordFlag && !payloadExists) {
                        tempPayloadFlag = validateDataParam(requiredParameterNode);
                    }
                    consumerRecordsExists = consumerRecordsExists ? consumerRecordsExists : tempConsumerRecordFlag;
                    payloadExists = payloadExists ? payloadExists : tempPayloadFlag;
                    break;
                case INTERSECTION_TYPE_DESC:
                    if (!consumerRecordsExists) {
                        tempConsumerRecordFlag = validateReadonlyConsumerRecordsParam(requiredParameterNode);
                    }
                    if (!tempConsumerRecordFlag && !payloadExists) {
                        tempPayloadFlag = validateReadonlyDataParam(requiredParameterNode);
                    }
                    consumerRecordsExists = consumerRecordsExists ? consumerRecordsExists : tempConsumerRecordFlag;
                    payloadExists = payloadExists ? payloadExists : tempPayloadFlag;
                    break;
                case QUALIFIED_NAME_REFERENCE:
                    callerExists = validateCallerParam(requiredParameterNode);
                    break;
                default:
                    break;
            }
        }
        validateParameterTypeResults(parameters, location, callerExists, consumerRecordsExists, payloadExists);
    }

    private void validateParameterTypeResults(SeparatedNodeList<ParameterNode> parameters, Location location,
                                              boolean callerExists, boolean consumerRecordsExists,
                                              boolean payloadExists) {
        if (parameters.size() == 3) {
            if (!callerExists || !consumerRecordsExists || !payloadExists) {
                reportErrorDiagnostic(INVALID_PARAM_TYPES, location);
            }
        } else if (parameters.size() == 2) {
            if ((!callerExists || !consumerRecordsExists) && (!callerExists || !payloadExists)
                    && (!payloadExists || !consumerRecordsExists)) {
                reportErrorDiagnostic(INVALID_PARAM_TYPES, location);
            }
        } else if (!consumerRecordsExists && !payloadExists) {
            reportErrorDiagnostic(INVALID_SINGLE_PARAMETER, location);
        }
    }

    private boolean validateCallerParam(RequiredParameterNode requiredParameterNode) {
        Node parameterTypeNode = requiredParameterNode.typeName();
        Optional<Symbol> paramSymbol = semanticModel.symbol(parameterTypeNode);
        if (paramSymbol.isPresent()) {
            Optional<ModuleSymbol> moduleSymbol = paramSymbol.get().getModule();
            if (moduleSymbol.isPresent()) {
                String paramName = paramSymbol.get().getName().isPresent() ? paramSymbol.get().getName().get() : "";
                if (validateModuleId(moduleSymbol.get()) && paramName.equals(CALLER)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean validateConsumerRecordsParam(RequiredParameterNode requiredParameterNode) {
        boolean hasPayloadAnnotation = requiredParameterNode.annotations().stream()
                .anyMatch(annotationNode -> annotationNode.annotReference().toString().equals(PAYLOAD_ANNOTATION));
        if (hasPayloadAnnotation) {
            return false;
        }
        Node parameterTypeNode = requiredParameterNode.typeName();
        ArrayTypeDescriptorNode arrayTypeDescriptorNode = (ArrayTypeDescriptorNode) parameterTypeNode;
        TypeDescriptorNode memberType = arrayTypeDescriptorNode.memberTypeDesc();
        if (memberType.kind() != QUALIFIED_NAME_REFERENCE && memberType.kind() != SIMPLE_NAME_REFERENCE) {
            return false;
        }
        Optional<Symbol> symbol = semanticModel.symbol(memberType);
        return symbol.isPresent() && isConsumerRecordType((TypeReferenceTypeSymbol) symbol.get());
    }

    private boolean validateDataParam(RequiredParameterNode requiredParameterNode) {
        Node parameterTypeNode = requiredParameterNode.typeName();
        ArrayTypeDescriptorNode arrayTypeDescriptorNode = (ArrayTypeDescriptorNode) parameterTypeNode;
        TypeDescriptorNode memberType = arrayTypeDescriptorNode.memberTypeDesc();
        SyntaxKind syntaxKind = memberType.kind();
        if (syntaxKind == QUALIFIED_NAME_REFERENCE) {
            return !validateConsumerRecordsParam(requiredParameterNode);
        } else {
            return validateDataParamSyntaxKind(syntaxKind);
        }
    }

    private boolean validateDataParamSyntaxKind(SyntaxKind syntaxKind) {
        return syntaxKind == INT_TYPE_DESC || syntaxKind == STRING_TYPE_DESC || syntaxKind == BOOLEAN_TYPE_DESC ||
                syntaxKind == FLOAT_TYPE_DESC || syntaxKind == DECIMAL_TYPE_DESC || syntaxKind == RECORD_TYPE_DESC ||
                syntaxKind == SIMPLE_NAME_REFERENCE || syntaxKind == MAP_TYPE_DESC || syntaxKind == BYTE_TYPE_DESC ||
                syntaxKind == TABLE_TYPE_DESC || syntaxKind == JSON_TYPE_DESC || syntaxKind == XML_TYPE_DESC ||
                syntaxKind == ANYDATA_TYPE_DESC || syntaxKind == UNION_TYPE_DESC || syntaxKind == NIL_TYPE_DESC;
    }

    private boolean validateReadonlyConsumerRecordsParam(RequiredParameterNode requiredParameterNode) {
        boolean hasPayloadAnnotation = requiredParameterNode.annotations().stream()
                .anyMatch(annotationNode -> annotationNode.annotReference().toString().equals(PAYLOAD_ANNOTATION));
        if (hasPayloadAnnotation) {
            return false;
        }
        Node parameterTypeNode = requiredParameterNode.typeName();
        IntersectionTypeDescriptorNode typeDescriptorNode = (IntersectionTypeDescriptorNode) parameterTypeNode;
        Optional<TypeDescriptorNode> arrayTypeDescNode = Optional.empty();
        if (typeDescriptorNode.rightTypeDesc().kind() == ARRAY_TYPE_DESC) {
            arrayTypeDescNode = Optional.of(((ArrayTypeDescriptorNode) typeDescriptorNode.rightTypeDesc())
                    .memberTypeDesc());
        } else if (typeDescriptorNode.leftTypeDesc().kind() == ARRAY_TYPE_DESC) {
            arrayTypeDescNode = Optional.of(((ArrayTypeDescriptorNode) typeDescriptorNode.leftTypeDesc())
                    .memberTypeDesc());
        }
        if (arrayTypeDescNode.isEmpty() || (arrayTypeDescNode.get().kind() != QUALIFIED_NAME_REFERENCE &&
                arrayTypeDescNode.get().kind() != SIMPLE_NAME_REFERENCE)) {
            return false;
        }
        Optional<Symbol> typeSymbol = semanticModel.symbol(arrayTypeDescNode.get());
        if (typeSymbol.isPresent() && typeSymbol.get().kind() == SymbolKind.TYPE) {
            return isConsumerRecordType((TypeReferenceTypeSymbol) typeSymbol.get());
        }
        return false;
    }

    private boolean validateReadonlyDataParam(RequiredParameterNode requiredParameterNode) {
        Node parameterTypeNode = requiredParameterNode.typeName();
        IntersectionTypeDescriptorNode typeDescriptorNode = (IntersectionTypeDescriptorNode) parameterTypeNode;
        if (typeDescriptorNode.rightTypeDesc().kind() == ARRAY_TYPE_DESC) {
            SyntaxKind syntaxKind = ((ArrayTypeDescriptorNode) typeDescriptorNode.rightTypeDesc())
                    .memberTypeDesc().kind();
            return validateDataParamSyntaxKind(syntaxKind);
        } else if (typeDescriptorNode.leftTypeDesc().kind() == ARRAY_TYPE_DESC) {
            SyntaxKind syntaxKind = ((ArrayTypeDescriptorNode) typeDescriptorNode.leftTypeDesc())
                    .memberTypeDesc().kind();
            return validateDataParamSyntaxKind(syntaxKind);
        }
        return false;
    }

    private boolean isConsumerRecordType(TypeReferenceTypeSymbol typeReferenceTypeSymbol) {
        if (typeReferenceTypeSymbol.definition() instanceof ClassSymbol) {
            return false;
        }

        RecordTypeSymbol recordTypeSymbol = (RecordTypeSymbol) typeReferenceTypeSymbol.typeDescriptor();
        Map<String, RecordFieldSymbol> fieldDescriptors = recordTypeSymbol.fieldDescriptors();
        return validateRecordFields(fieldDescriptors);

//        TypeDefinitionSymbol typeDefinitionSymbol = (TypeDefinitionSymbol) typeReferenceTypeSymbol.definition();
//        return typeDefinitionSymbol.typeDescriptor().typeKind() == TypeDescKind.RECORD;
    }

    private boolean validateRecordFields(Map<String, RecordFieldSymbol> fieldDescriptors) {
        if (fieldDescriptors.size() != 4 || !fieldDescriptors.containsKey("key") ||
                !fieldDescriptors.containsKey("value") || !fieldDescriptors.containsKey("timestamp") ||
                !fieldDescriptors.containsKey("offset")) {
            return false;
        }
        if (fieldDescriptors.get("timestamp").typeDescriptor().typeKind() != TypeDescKind.INT) {
            return false;
        }
        if (fieldDescriptors.get("offset").typeDescriptor().typeKind() != TypeDescKind.TYPE_REFERENCE) {
            return false;
        }
        if (!validateOffsetField((TypeReferenceTypeSymbol) fieldDescriptors.get("offset").typeDescriptor())) {
            return false;
        }
        if (!validateAnydataFields(fieldDescriptors.get("key").typeDescriptor())) {
            return false;
        }
        if (!validateAnydataFields(fieldDescriptors.get("value").typeDescriptor())) {
            return false;
        }
        return true;
    }

    private boolean validateAnydataFields(TypeSymbol typeSymbol) {
        TypeDescKind symbolTypeKind = typeSymbol.typeKind();
        return symbolTypeKind == ANYDATA || symbolTypeKind == ARRAY || symbolTypeKind == BOOLEAN ||
                symbolTypeKind == JSON || symbolTypeKind == INT || symbolTypeKind == STRING ||
                symbolTypeKind == FLOAT || symbolTypeKind == DECIMAL || symbolTypeKind == RECORD ||
                symbolTypeKind == TABLE || symbolTypeKind == XML || symbolTypeKind == UNION ||
                symbolTypeKind == MAP || symbolTypeKind == ANYDATA || symbolTypeKind == NIL ||
                symbolTypeKind == TYPE_REFERENCE;
    }

    private boolean validateOffsetField(TypeReferenceTypeSymbol offsetTypeSymbol) {
        if (offsetTypeSymbol.typeDescriptor().typeKind() == TypeDescKind.RECORD) {
            Map<String, RecordFieldSymbol> partitionOffsetFieldDescriptors = ((RecordTypeSymbol) offsetTypeSymbol
                    .typeDescriptor()).fieldDescriptors();
            if (partitionOffsetFieldDescriptors.size() != 2 || !partitionOffsetFieldDescriptors
                    .containsKey("partition") || !partitionOffsetFieldDescriptors.containsKey("offset")) {
                return false;
            }
            if (partitionOffsetFieldDescriptors.get("offset").typeDescriptor().typeKind() != TypeDescKind.INT) {
                return false;
            }
            if (partitionOffsetFieldDescriptors.get("partition").typeDescriptor().typeKind()
                    != TypeDescKind.TYPE_REFERENCE) {
                return false;
            }
            TypeReferenceTypeSymbol partitionTypeRefTypeSymbol = (TypeReferenceTypeSymbol)
                    partitionOffsetFieldDescriptors.get("partition").typeDescriptor();
            if (partitionTypeRefTypeSymbol.typeDescriptor().typeKind() != TypeDescKind.RECORD) {
                return false;
            }
            RecordTypeSymbol partitionRecordTypeSymbol = (RecordTypeSymbol) partitionTypeRefTypeSymbol.typeDescriptor();
            Map<String, RecordFieldSymbol> topicPartitionFieldDescs = partitionRecordTypeSymbol.fieldDescriptors();
            if (topicPartitionFieldDescs.size() != 2 || !topicPartitionFieldDescs.containsKey("topic") ||
                    !topicPartitionFieldDescs.containsKey("partition")) {
                return false;
            }
            if (topicPartitionFieldDescs.get("topic").typeDescriptor().typeKind() != TypeDescKind.STRING) {
                return false;
            }
            if (topicPartitionFieldDescs.get("partition").typeDescriptor().typeKind() != TypeDescKind.INT) {
                return false;
            }
        }
        return true;
    }

    private void validateReturnTypeErrorOrNil(FunctionDefinitionNode functionDefinitionNode) {
        MethodSymbol methodSymbol = getMethodSymbol(context, functionDefinitionNode);
        if (methodSymbol != null) {
            Optional<TypeSymbol> returnTypeDesc = methodSymbol.typeDescriptor().returnTypeDescriptor();
            if (returnTypeDesc.isPresent()) {
                if (returnTypeDesc.get().typeKind() == TypeDescKind.UNION) {
                    List<TypeSymbol> returnTypeMembers =
                            ((UnionTypeSymbol) returnTypeDesc.get()).memberTypeDescriptors();
                    for (TypeSymbol returnType : returnTypeMembers) {
                        if (returnType.typeKind() != TypeDescKind.NIL) {
                            if (returnType.typeKind() == TypeDescKind.TYPE_REFERENCE) {
                                if (!returnType.signature().equals(PluginConstants.ERROR) &&
                                        !validateModuleId(returnType.getModule().get())) {
                                    reportErrorDiagnostic(INVALID_RETURN_TYPE_ERROR_OR_NIL,
                                            functionDefinitionNode.location());
                                }
                            } else if (returnType.typeKind() != TypeDescKind.ERROR) {
                                reportErrorDiagnostic(INVALID_RETURN_TYPE_ERROR_OR_NIL,
                                        functionDefinitionNode.location());
                            }
                        }
                    }
                } else if (returnTypeDesc.get().typeKind() != TypeDescKind.NIL) {
                    reportErrorDiagnostic(INVALID_RETURN_TYPE_ERROR_OR_NIL, functionDefinitionNode.location());
                }
            }
        }
    }

    public void reportErrorDiagnostic(PluginConstants.CompilationErrors error, Location location) {
        context.reportDiagnostic(getDiagnostic(error, ERROR, location));
    }
}
