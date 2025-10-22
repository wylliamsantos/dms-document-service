package br.com.dms.service.workflow.pojo;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum TypeFieldMetadata implements CastFieldMetadata{
    STRING("string"){
        @Override
        public String cast(Object value){
            return value.toString();
        }
    },

    INTEGER("integer"){
        @Override
        public Integer cast(Object value){
            return Integer.getInteger(value.toString());
        }
    };


    private final String name;
    TypeFieldMetadata(String name) {
        this.name = name;
    }

    public static TypeFieldMetadata get(String name){
        return Arrays.stream(values()).filter(value -> value.getName().equals(name)).findFirst().orElse(null);
    }
}
