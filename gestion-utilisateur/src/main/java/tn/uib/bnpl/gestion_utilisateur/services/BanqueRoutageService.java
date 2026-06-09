package tn.uib.bnpl.gestion_utilisateur.services;

import tn.uib.bnpl.gestion_utilisateur.dto.AnalysteRoutageDto;

import java.util.List;

public interface BanqueRoutageService {

    List<AnalysteRoutageDto> listerAnalystesActifsParCodeBanque(String codeBanque);
}
