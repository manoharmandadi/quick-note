package com.pm.patientservice.controller;

import com.pm.patientservice.dto.PatientRequestDto;
import com.pm.patientservice.dto.PatientResponseDto;
import com.pm.patientservice.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
@Tag(name = "Patient Controller", description = "APIs for managing patients")
public class PatientContoller {

    private PatientService patientService;

    public PatientContoller(PatientService patientService){
        this.patientService = patientService;
    }

    @GetMapping
    @Operation(summary = "Get All Patients", description = "Retrieve a list of all patients")
    public List<PatientResponseDto> getPatients(){
        return patientService.getPatients();
    }

    @PostMapping
    @Operation(summary = "Create Patient", description = "Create a new patient with the provided details")
    public PatientResponseDto createPatient( @Valid @RequestBody PatientRequestDto patientRequestDto){
        PatientResponseDto patientResponseDto = patientService.createPatient(patientRequestDto);
        return patientResponseDto;
    }

    @PutMapping("/{id}")
    public PatientResponseDto updatePatient(@PathVariable("id") UUID id, @RequestBody PatientRequestDto patientRequestDto){
        PatientResponseDto patientResponseDto = patientService.updatePatient(id, patientRequestDto);
        return patientResponseDto;
    }

}
