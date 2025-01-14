/*
 * Copyright © 2020-2035 erupt.xyz All rights reserved.
 * Author: YuePeng (erupts@126.com)
 */

import xyz.erupt.annotation.*;
import xyz.erupt.annotation.sub_erupt.*;
import xyz.erupt.annotation.sub_field.*;
import xyz.erupt.annotation.sub_field.sub_edit.*;
import xyz.erupt.auth.model.base.*;
import javax.persistence.*;
import java.util.Set;
import xyz.erupt.jpa.model.BaseModel;
import java.util.Date;

<#assign erupt=rows[0]/>
@Erupt(name = "${erupt.name}")
@Table(name = "${erupt.tableName}")
@Entity
public class ${erupt.className} extends BaseModel {

    <#list erupt.fields?sort_by('sort') as field>
        <#assign type = GeneratorType.valueOf(field.type) />
        @EruptField(
                views = @View(
                        title = "${field.showName}"${field.sortable?string(', sortable = true', '')}${field.isShow?string('', ', show = false')}
                ),
                edit = @Edit(
                        title = "${field.showName}",
                        type = EditType.${type.mapping.name()}${field.isShow?string('', ', show = false')}${field.notNull?string(', notNull = true', '')}<#if type.code??>${',
                        ' + type.code}</#if>
                )
        )
        private ${GeneratorType.replaceLinkClass(type, erupt.className, field.linkClass!)} ${field.fieldName};

    </#list>
}