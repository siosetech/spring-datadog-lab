package tech.sioseforge.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import tech.sioseforge.auth.domain.entity.DashboardPermission;
import tech.sioseforge.auth.domain.view.PermissionVO;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {

    @Mapping(source = "dashboard.id", target = "dashboardId")
    @Mapping(source = "dashboard.name", target = "dashboardName")
    PermissionVO toPermissionVO(DashboardPermission permission);

    List<PermissionVO> toPermissionVOList(List<DashboardPermission> permissions);
}
