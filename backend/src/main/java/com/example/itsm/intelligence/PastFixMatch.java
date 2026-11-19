package com.example.itsm.intelligence;

import com.example.itsm.incident.Incident;

/** A resolved incident whose fix may apply to a new problem, with how closely its text matches. */
public record PastFixMatch(Incident incident, double score) {
}
