package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.service.dto.PushSubscriptionDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PushSubscriptionMapper extends EntityMapper<PushSubscriptionDTO, PushSubscription> {
}
