/*
 * Copyright 2019 The Chromium Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

var request = null;

/**
 * Create an instance of PaymentRequest.
 * @param {DOMString} supportedMethods - The payment method name. If absent,
 * then the page URL is used instead.
 */
function create(supportedMethods) { // eslint-disable-line no-unused-vars
  if (!supportedMethods) {
    supportedMethods = window.location.href;
  }
  try {
    request = new PaymentRequest([{supportedMethods}], {
      total:
          {label: 'PENDING TOTAL', amount: {currency: 'USD', value: '99.99'}},
    });
  } catch (error) {
    print(error.message);
  }
}

/**
 * Launch PaymentRequest with a show promise for digital goods.
 */
function buy() { // eslint-disable-line no-unused-vars
  try {
    request
        .show(new Promise(function(resolve) {
          resolve({
            total: {label: 'Total', amount: {currency: 'USD', value: '1.00'}},
          });
        }))
        .then(function(result) {
          print(JSON.stringify(result.details));
          return result.complete('success');
        })
        .catch(function(error) {
          print(error);
        });
  } catch (error) {
    print(error);
  }
}
