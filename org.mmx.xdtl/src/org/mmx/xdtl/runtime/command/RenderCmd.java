package org.mmx.xdtl.runtime.command;

import java.io.StringWriter;
import java.util.List;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.mmx.xdtl.model.Variable;
import org.mmx.xdtl.runtime.Context;
import org.mmx.xdtl.runtime.RuntimeCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class RenderCmd implements RuntimeCommand {
    private final Logger m_logger = LoggerFactory.getLogger(RenderCmd.class);
    private final String m_template;
    private final Mappings m_source;
    private final Object m_rowset;
    private final String m_targetVarName;
    private final List<Variable> m_parameters;
    private VelocityEngine m_velocity;

    public RenderCmd(String template, Mappings source, String targetVarName,
            Object rowset, List<Variable> parameters) {
        super();
        m_template = template;
        m_source = source;
        m_targetVarName = targetVarName;
        m_rowset = rowset;
        m_parameters = parameters;
    }

    @Override
    public void run(Context context) throws Throwable {
        m_logger.info(String.format("render: template='%s', source='%s'", m_template, m_source));
        Template t = m_velocity.getTemplate(m_template);
        StringWriter writer = new StringWriter();
        VelocityContext velocityContext = new VelocityContext();
        
        if (m_source != null) {
            velocityContext.put("Targets", m_source.getTargets());
            velocityContext.put("Sources", m_source.getSources());
            velocityContext.put("Columns", m_source.getColumns());        
            velocityContext.put("Conditions", m_source.getConditions());
        }
        
        if (m_rowset != null) {
            velocityContext.put("Rows", m_rowset);
        }
        
        for (Variable var: m_parameters) {
            velocityContext.put(var.getName(), var.getValue());
        }
        
        t.merge(velocityContext, writer);
        StringBuffer buf = writer.getBuffer();
        String result;
        
        if (buf.length() > 0 && buf.charAt(0) == '\ufeff') {
            result = buf.substring(1);
        } else {
            result = buf.toString();
        }
        
        result = result.trim();
        m_logger.debug("render result: {}", result);
        context.assignVariable(m_targetVarName, result);
    }

    @Inject
    public void setVelocity(VelocityEngine velocity) {
        m_velocity = velocity;
    }
}