package com.hayden.persistence.examples;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.SqlTypes;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

public class VectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return SqlTypes.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals((float[]) x, (float[]) y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode((float[]) x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
            PGobject pgObject = (PGobject) rs.getObject(position);
            if (pgObject == null) {
                return null;
            }
            String value = pgObject.getValue();
            return parseVector(value);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            PGobject pgObject = new PGobject();
            pgObject.setType("vector");
            pgObject.setValue(vectorToString(value));
            st.setObject(index, pgObject);
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : value.clone();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return (Serializable) deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }

    @Override
    public float[] replace(float[] original, float[] target, Object owner) {
        return deepCopy(original);
    }

    private float[] parseVector(String value) {
        String[] tokens = value.replaceAll("\\[", "").replace("]", "").trim().split(",");
        float[] vector = new float[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            vector[i] = Float.parseFloat(tokens[i].trim());
        }
        return vector;
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
