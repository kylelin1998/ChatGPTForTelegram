package code.repository;

import code.config.Config;
import code.eneity.GptTokenTableEntity;
import code.eneity.cons.GptTokenStatusEnum;
import code.eneity.cons.YesOrNoEnum;
import code.repository.mapper.TableRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

import java.util.List;

@Slf4j
public class GptTokenTableRepository extends TableRepository<GptTokenTableEntity> {

    public GptTokenTableRepository() {
        super(Config.DBPath);
    }

    public void deleteAll() {
        super.deleteAll(GptTokenTableEntity.class);
    }
    public synchronized void forceSave(List<String> tokens) {
        super.deleteAll(GptTokenTableEntity.class);
        for (String token : tokens) {
            GptTokenTableEntity entity = new GptTokenTableEntity();
            entity.setToken(token);
            entity.setStatus(GptTokenStatusEnum.Alive.getNum());
            entity.setSend(YesOrNoEnum.No.getNum());
            super.insert(entity);
        }
    }

    public List<GptTokenTableEntity> selectListByStatus(GptTokenStatusEnum gptTokenStatusEnum) {
        GptTokenTableEntity where = new GptTokenTableEntity();
        where.setStatus(gptTokenStatusEnum.getNum());
        return super.selectList(where);
    }

    public GptTokenTableEntity selectOneByRand(GptTokenStatusEnum gptTokenStatusEnum) {
        GptTokenTableEntity where = new GptTokenTableEntity();
        where.setStatus(gptTokenStatusEnum.getNum());
        return super.selectOneByRand(where);
    }

    public boolean die(String gptToken) {
        GptTokenTableEntity where = new GptTokenTableEntity();
        where.setToken(gptToken);

        GptTokenTableEntity entity = new GptTokenTableEntity();
        entity.setStatus(GptTokenStatusEnum.Die.getNum());

        return BooleanUtils.toBooleanDefaultIfNull(super.update(entity, where), false);
    }
    public boolean dieAndSend(String gptToken) {
        GptTokenTableEntity where = new GptTokenTableEntity();
        where.setToken(gptToken);

        GptTokenTableEntity entity = new GptTokenTableEntity();
        entity.setStatus(GptTokenStatusEnum.Die.getNum());
        entity.setSend(YesOrNoEnum.Yes.getNum());

        return BooleanUtils.toBooleanDefaultIfNull(super.update(entity, where), false);
    }

}
