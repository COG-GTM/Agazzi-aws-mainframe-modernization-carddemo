package com.carddemo.ext.authorization.web;

/**
 * Local adapter for the COMMAREA fields CDEMO-CPVS-INFO / CDEMO-CPVD-INFO that the frozen
 * ConversationContext does not carry (see CONTRACT-CHANGE in the PR).
 */
public final class AuthViewState {
  Long acctId;
  Long custId;
  final String[] authKeys = new String[5];
  final String[] prevPg = new String[21];
  String last;
  int pageNum;
  boolean nextPage;
  String selected;
}
