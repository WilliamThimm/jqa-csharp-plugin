package org.jqassistant.contrib.plugin.csharp.json_to_neo4j;

import com.buschmais.jqassistant.core.store.api.Store;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.caches.CSharpFileCache;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.caches.EnumValueCache;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.caches.NamespaceCache;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.caches.TypeCache;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.json_model.ClassModel;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.json_model.ConstructorModel;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.json_model.EnumMemberModel;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.json_model.EnumModel;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.json_model.FileModel;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.json_model.InterfaceModel;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.json_model.TypeModel;
import org.jqassistant.contrib.plugin.csharp.json_to_neo4j.json_model.UsingModel;
import org.jqassistant.contrib.plugin.csharp.model.CSharpFileDescriptor;
import org.jqassistant.contrib.plugin.csharp.model.ClassDescriptor;
import org.jqassistant.contrib.plugin.csharp.model.ConstructorDescriptor;
import org.jqassistant.contrib.plugin.csharp.model.EnumTypeDescriptor;
import org.jqassistant.contrib.plugin.csharp.model.EnumValueDescriptor;
import org.jqassistant.contrib.plugin.csharp.model.InterfaceTypeDescriptor;
import org.jqassistant.contrib.plugin.csharp.model.NamespaceDescriptor;
import org.jqassistant.contrib.plugin.csharp.model.TypeDescriptor;
import org.jqassistant.contrib.plugin.csharp.model.UsesNamespaceDescriptor;

import java.util.List;
import java.util.Optional;

public class TypeAnalyzer {

    private final Store store;

    private final TypeCache typeCache;
    private final NamespaceCache namespaceCache;
    private final CSharpFileCache fileCache;
    private final EnumValueCache enumValueCache;

    public TypeAnalyzer(Store store, NamespaceCache namespaceCache, CSharpFileCache fileCache, EnumValueCache enumValueCache, TypeCache typeCache) {
        this.store = store;
        this.typeCache = typeCache;
        this.namespaceCache = namespaceCache;
        this.fileCache = fileCache;
        this.enumValueCache = enumValueCache;
    }

    protected void createUsings(List<FileModel> fileModelList) {

        for (FileModel fileModel : fileModelList) {
            CSharpFileDescriptor cSharpFileDescriptor = fileCache.get(fileModel.getAbsolutePath());

            for (UsingModel usingModel : fileModel.getUsings()) {
                NamespaceDescriptor namespaceDescriptor = namespaceCache.findOrCreate(usingModel.getKey());

                UsesNamespaceDescriptor usesNamespaceDescriptor = store.create(cSharpFileDescriptor, UsesNamespaceDescriptor.class, namespaceDescriptor);
                usesNamespaceDescriptor.setAlias(usingModel.getAlias());
            }
        }
    }

    protected void createTypes(List<FileModel> fileModelList) {

        for (FileModel fileModel : fileModelList) {
            CSharpFileDescriptor cSharpFileDescriptor = fileCache.get(fileModel.getAbsolutePath());

            for (ClassModel classModel : fileModel.getClasses()) {
                createType(cSharpFileDescriptor, classModel);
            }

            for (EnumModel enumModel : fileModel.getEnums()) {
                createType(cSharpFileDescriptor, enumModel);
            }

            for (InterfaceModel interfaceModel : fileModel.getInterfaces()) {
                createType(cSharpFileDescriptor, interfaceModel);
            }
        }
    }

    protected void createType(CSharpFileDescriptor cSharpFileDescriptor, TypeModel typeModel) {

        TypeDescriptor typeDescriptor = typeCache.create(typeModel);
        fillDescriptor(typeDescriptor, typeModel);
        cSharpFileDescriptor.getTypes().add(typeDescriptor);

        findOrCreateNamespace(typeModel.getFqn())
                .ifPresent(namespaceDescriptor -> namespaceDescriptor.getContains().add(typeDescriptor));
    }

    protected Optional<NamespaceDescriptor> findOrCreateNamespace(String fqn) {
        if (!fqn.contains(".")) {
            return Optional.empty();
        }

        String namespaceFqn = fqn.substring(0, fqn.lastIndexOf("."));
        return Optional.of(namespaceCache.findOrCreate(namespaceFqn));
    }

    protected void linkBaseTypes(List<FileModel> fileModelList) {

        for (FileModel fileModel : fileModelList) {
            for (ClassModel classModel : fileModel.getClasses()) {
                ClassDescriptor classDescriptor = (ClassDescriptor) typeCache.findAny(classModel.getKey());

                if (StringUtils.isNotBlank(classModel.getBaseType())) {
                    TypeDescriptor typeDescriptor = typeCache.findOrCreate(classModel.getBaseType());
                    classDescriptor.setSuperClass(typeDescriptor);
                }
            }
        }
    }

    protected void linkInterfaces(List<FileModel> fileModelList) {

        for (FileModel fileModel : fileModelList) {
            for (ClassModel classModel : fileModel.getClasses()) {
                ClassDescriptor classDescriptor = (ClassDescriptor) typeCache.findAny(classModel.getKey());

                if (CollectionUtils.isNotEmpty(classModel.getImplementedInterfaces())) {
                    for (String interfaceFqn : classModel.getImplementedInterfaces()) {
                        TypeDescriptor typeDescriptor = typeCache.findOrCreate(interfaceFqn);
                        classDescriptor.getInterfaces().add(typeDescriptor);
                    }
                }
            }

            for (InterfaceModel interfaceModel : fileModel.getInterfaces()) {
                InterfaceTypeDescriptor interfaceTypeDescriptor = (InterfaceTypeDescriptor) typeCache.findAny(interfaceModel.getKey());

                if (CollectionUtils.isNotEmpty(interfaceModel.getImplementedInterfaces())) {
                    for (String interfaceFqn : interfaceModel.getImplementedInterfaces()) {
                        TypeDescriptor typeDescriptor = typeCache.findOrCreate(interfaceFqn);
                        interfaceTypeDescriptor.getInterfaces().add(typeDescriptor);
                    }
                }
            }
        }
    }

    public void createEnumMembers(List<FileModel> fileModelList) {

        for (FileModel fileModel : fileModelList) {
            for (EnumModel enumModel : fileModel.getEnums()) {
                EnumTypeDescriptor enumTypeDescriptor = (EnumTypeDescriptor) typeCache.findAny(enumModel.getKey());

                for (EnumMemberModel enumMemberModel : enumModel.getMembers()) {
                    EnumValueDescriptor enumValueDescriptor = enumValueCache.create(enumMemberModel.getKey());
                    enumValueDescriptor.setType(enumTypeDescriptor);
                }
            }
        }
    }

    public void createConstructors(List<FileModel> fileModelList) {
        for (FileModel fileModel : fileModelList) {
            for (ClassModel classModel : fileModel.getClasses()) {

                Optional<TypeDescriptor> descriptor = typeCache.findTypeByRelativePath(classModel.getKey(), fileModel.getRelativePath());
                if (!descriptor.isPresent()) continue;

                ClassDescriptor classDescriptor = (ClassDescriptor) descriptor.get();
                for (ConstructorModel constructorModel : classModel.getConstructors()) {
                    ConstructorDescriptor constructorDescriptor = store.create(ConstructorDescriptor.class);
                    constructorDescriptor.setName(constructorModel.getName());
                    constructorDescriptor.setVisibility(constructorModel.getAccessibility());
                    constructorDescriptor.setFirstLineNumber(constructorModel.getFirstLineNumber());
                    constructorDescriptor.setLastLineNumber(constructorModel.getLastLineNumber());
                    constructorDescriptor.setEffectiveLineCount(constructorModel.getEffectiveLineCount());

                    classDescriptor.getDeclaredMembers().add(constructorDescriptor);
                }
            }
        }

    }




    protected void fillDescriptor(TypeDescriptor descriptor, TypeModel typeModel) {
        descriptor.setName(typeModel.getName());
        descriptor.setFullQualifiedName(typeModel.getFqn());
        descriptor.setMd5(typeModel.getMd5());
        descriptor.setRelativePath(typeModel.getRelativePath());
        descriptor.setFirstLineNumber(typeModel.getFirstLineNumber());
        descriptor.setLastLineNumber(typeModel.getLastLineNumber());
        descriptor.setEffectiveLineCount(typeModel.getEffectiveLineCount());
        descriptor.setRelativePath(typeModel.getRelativePath());

        if (typeModel instanceof InterfaceModel && descriptor instanceof InterfaceTypeDescriptor){
            InterfaceModel interfaceModel = (InterfaceModel) typeModel;
            InterfaceTypeDescriptor interfaceDescriptor = (InterfaceTypeDescriptor) descriptor;
            interfaceDescriptor.setVisibility(interfaceModel.getAccessibility());
            interfaceDescriptor.setPartial(interfaceModel.isPartial());
        }

        if (typeModel instanceof ClassModel && descriptor instanceof ClassDescriptor) {
            ClassModel classModel = (ClassModel) typeModel;
            ClassDescriptor classDescriptor = (ClassDescriptor) descriptor;
            classDescriptor.setPartial(classModel.isPartial());
            classDescriptor.setAbstract(classModel.isAbstractKeyword());
            classDescriptor.setSealed(classModel.isSealed());
            classDescriptor.setStatic(classModel.isStaticKeyword());
        }
    }
}