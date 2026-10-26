package it.pissir.rail.common.msg;

/** Avviso della centrale a un treno, ad esempio un guasto sulla linea o un ritardo. Topic: train/{id}/alert */
public record Alert(String message, String ts) {
}
